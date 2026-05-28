package com.orderapi.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Order {

    private final UUID id;
    private final UUID customerId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private final Instant createdAt;
    private Instant updatedAt;

    public Order(UUID id, UUID customerId, List<OrderItem> items, OrderStatus status,
                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.items = List.copyOf(items);
        this.status = status;
        this.totalAmount = calculateTotal(items);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Order create(UUID customerId, List<OrderItem> items) {
        var now = Instant.now();
        return new Order(UUID.randomUUID(), customerId, items, OrderStatus.PENDING, now, now);
    }

    public void transitionTo(OrderStatus target) {
        if (target == OrderStatus.CANCELLED && !status.isCancellable()) {
            throw new IllegalStateException(
                    "Order %s cannot be cancelled in status %s".formatted(id, status));
        }
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid transition from %s to %s for order %s".formatted(status, target, id));
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    private static BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public List<OrderItem> getItems() { return items; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
