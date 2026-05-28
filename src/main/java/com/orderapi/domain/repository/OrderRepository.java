package com.orderapi.domain.repository;

import com.orderapi.domain.model.Order;
import com.orderapi.domain.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(UUID id);
    Page<Order> findAll(OrderStatus status, Pageable pageable);
}
