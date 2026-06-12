package com.orderapi.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ObjectMapper objectMapper;

    public OrderEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders.status.changed", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderStatusChanged(String message) {
        try {
            var event = objectMapper.readValue(message, OrderStatusChangedEvent.class);
            log.info("Received order status change: id={}, status={}, customerId={}, updatedAt={}",
                    event.id(), event.status(), event.customerId(), event.updatedAt());
        } catch (Exception e) {
            log.error("Failed to process order status event: {}", message, e);
        }
    }
}
