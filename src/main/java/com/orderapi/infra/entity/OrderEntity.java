package com.orderapi.infra.entity;

import com.orderapi.domain.model.Order;
import com.orderapi.domain.model.OrderItem;
import com.orderapi.domain.model.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {}

    public static OrderEntity fromDomain(Order order) {
        var entity = new OrderEntity();
        entity.id = order.getId();
        entity.customerId = order.getCustomerId();
        entity.status = order.getStatus();
        entity.totalAmount = order.getTotalAmount();
        entity.createdAt = order.getCreatedAt();
        entity.updatedAt = order.getUpdatedAt();
        entity.items = order.getItems().stream()
                .map(item -> OrderItemEntity.fromDomain(item, entity))
                .toList();
        return entity;
    }

    public Order toDomain() {
        var domainItems = items.stream()
                .map(OrderItemEntity::toDomain)
                .toList();
        return new Order(id, customerId, domainItems, status, createdAt, updatedAt);
    }

    public UUID getId() { return id; }
    public OrderStatus getStatus() { return status; }
}
