package com.orderapi.application.usecase;

import com.orderapi.api.dto.CreateOrderRequest;
import com.orderapi.domain.model.Order;
import com.orderapi.domain.model.OrderItem;
import com.orderapi.domain.model.OutboxEvent;
import com.orderapi.domain.repository.OrderRepository;
import com.orderapi.domain.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public CreateOrderUseCase(OrderRepository orderRepository, OutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order execute(CreateOrderRequest request, UUID authenticatedUserId) {
        var items = request.items().stream()
                .map(r -> new OrderItem(
                        UUID.randomUUID(), null, r.productId(),
                        r.productName(), r.quantity(), r.unitPrice()))
                .toList();

        var order = Order.create(authenticatedUserId, items);
        var saved = orderRepository.save(order);

        var event = OutboxEvent.create(
                saved.getId(),
                "ORDER_CREATED",
                toJson(saved));
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
}
