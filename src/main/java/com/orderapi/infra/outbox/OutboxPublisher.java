package com.orderapi.infra.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderapi.domain.repository.OutboxRepository;
import com.orderapi.infra.kafka.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;
    private static final String TOPIC = "orders.status.changed";

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
            var payload = toEventPayload(event.getPayload());
            kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), payload);
            log.info("Published to {}: type={}, aggregateId={}",
                    TOPIC, event.getEventType(), event.getAggregateId());
            outboxRepository.markPublished(event);
        }
        if (!events.isEmpty()) {
            log.info("Published {} outbox events to Kafka", events.size());
        }
    }

    private String toEventPayload(String orderJson) {
        try {
            var event = objectMapper.readValue(orderJson, OrderStatusChangedEvent.class);
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build event payload", e);
        }
    }
}
