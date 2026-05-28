package com.orderapi.api.dto;

import com.orderapi.domain.model.Order;
import com.orderapi.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        var items = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.id(), i.productId(), i.productName(),
                        i.quantity(), i.unitPrice(), i.subtotal()))
                .toList();
        return new OrderResponse(
                order.getId(), order.getCustomerId(), order.getStatus(),
                order.getTotalAmount(), items, order.getCreatedAt(), order.getUpdatedAt());
    }
}
