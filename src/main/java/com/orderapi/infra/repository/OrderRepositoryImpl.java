package com.orderapi.infra.repository;

import com.orderapi.domain.model.Order;
import com.orderapi.domain.model.OrderStatus;
import com.orderapi.domain.repository.OrderRepository;
import com.orderapi.infra.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final JpaOrderRepository jpa;

    public OrderRepositoryImpl(JpaOrderRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Order save(Order order) {
        var entity = OrderEntity.fromDomain(order);
        return jpa.save(entity).toDomain();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpa.findById(id).map(OrderEntity::toDomain);
    }

    @Override
    public Page<Order> findAll(OrderStatus status, Pageable pageable) {
        if (status != null) {
            return jpa.findByStatus(status, pageable).map(OrderEntity::toDomain);
        }
        return jpa.findAll(pageable).map(OrderEntity::toDomain);
    }
}
