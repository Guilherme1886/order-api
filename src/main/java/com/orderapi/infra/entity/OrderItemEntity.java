package com.orderapi.infra.entity;

import com.orderapi.domain.model.OrderItem;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    protected OrderItemEntity() {}

    public static OrderItemEntity fromDomain(OrderItem item, OrderEntity order) {
        var entity = new OrderItemEntity();
        entity.id = item.id();
        entity.order = order;
        entity.productId = item.productId();
        entity.productName = item.productName();
        entity.quantity = item.quantity();
        entity.unitPrice = item.unitPrice();
        return entity;
    }

    public OrderItem toDomain() {
        return new OrderItem(id, order.getId(), productId, productName, quantity, unitPrice);
    }
}
