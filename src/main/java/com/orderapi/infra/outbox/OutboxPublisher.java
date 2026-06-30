package com.orderapi.infra.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderapi.domain.repository.OutboxRepository;
import com.orderapi.infra.kafka.OrderCreatedEvent;
import com.orderapi.infra.kafka.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private static final String ORDER_CREATED_EVENT = "ORDER_CREATED";
    private static final String ORDERS_CREATED_TOPIC = "orders.created";
    private static final String STATUS_CHANGED_TOPIC = "orders.status.changed";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.publish-interval-ms}")
    public void publishPendingEvents() {
        var events = outboxRepository.findPending(BATCH_SIZE);
        for (var event : events) {
            // ORDER_CREATED inaugura o Saga e vai para orders.created (consumido pelo
            // payment-api); os demais eventos seguem no tópico de mudança de status.
            boolean isOrderCreated = ORDER_CREATED_EVENT.equals(event.getEventType());
            var topic = isOrderCreated ? ORDERS_CREATED_TOPIC : STATUS_CHANGED_TOPIC;
            var payload = isOrderCreated
                    ? toOrderCreatedPayload(event.getPayload())
                    : toStatusChangedPayload(event.getPayload());

            kafkaTemplate.send(topic, event.getAggregateId().toString(), payload);
            log.info("Published to {}: type={}, aggregateId={}",
                    topic, event.getEventType(), event.getAggregateId());
            outboxRepository.markPublished(event);
        }
        if (!events.isEmpty()) {
            log.info("Published {} outbox events to Kafka", events.size());
        }
    }

    /** Projeta o pedido completo (armazenado no outbox) no contrato de orders.created. */
    private String toOrderCreatedPayload(String orderJson) {
        try {
            var node = objectMapper.readTree(orderJson);
            var event = new OrderCreatedEvent(
                    UUID.fromString(node.get("id").asText()),
                    UUID.fromString(node.get("customerId").asText()),
                    new BigDecimal(node.get("totalAmount").asText()));
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build orders.created payload", e);
        }
    }

    private String toStatusChangedPayload(String orderJson) {
        try {
            var event = objectMapper.readValue(orderJson, OrderStatusChangedEvent.class);
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build orders.status.changed payload", e);
        }
    }
}
