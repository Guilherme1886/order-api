package com.orderapi.infra.repository;

import com.orderapi.domain.model.OutboxEvent;
import com.orderapi.domain.model.OutboxEvent.OutboxStatus;
import com.orderapi.domain.repository.OutboxRepository;
import com.orderapi.infra.entity.OutboxEventEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OutboxRepositoryImpl implements OutboxRepository {

    private final JpaOutboxRepository jpa;

    public OutboxRepositoryImpl(JpaOutboxRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(OutboxEvent event) {
        jpa.save(OutboxEventEntity.fromDomain(event));
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return jpa.findByStatus(OutboxStatus.PENDING, PageRequest.of(0, limit))
                .stream()
                .map(OutboxEventEntity::toDomain)
                .toList();
    }

    @Override
    public void markPublished(OutboxEvent event) {
        event.markPublished();
        jpa.save(OutboxEventEntity.fromDomain(event));
    }
}
