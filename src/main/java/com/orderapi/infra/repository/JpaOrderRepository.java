package com.orderapi.infra.repository;

import com.orderapi.infra.entity.OrderEntity;
import com.orderapi.domain.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaOrderRepository extends JpaRepository<OrderEntity, UUID> {
    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);
}
