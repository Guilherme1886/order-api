package com.orderapi.infra.repository;

import com.orderapi.domain.model.OutboxEvent.OutboxStatus;
import com.orderapi.infra.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findByStatus(OutboxStatus status, Pageable pageable);
}
