package com.orderapi.application.usecase;

import com.orderapi.domain.model.Order;
import com.orderapi.domain.model.OrderStatus;
import com.orderapi.domain.model.OutboxEvent;
import com.orderapi.domain.repository.OrderRepository;
import com.orderapi.domain.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateOrderStatusUseCase {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public UpdateOrderStatusUseCase(OrderRepository orderRepository,
                                    OutboxRepository outboxRepository,
                                    ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order execute(UUID orderId, OrderStatus newStatus) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.transitionTo(newStatus);
        var saved = orderRepository.save(order);

        var eventType = "ORDER_STATUS_CHANGED_TO_" + newStatus.name();
        var event = OutboxEvent.create(saved.getId(), eventType, toJson(saved));
        outboxRepository.save(event);

        return saved;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }
}
