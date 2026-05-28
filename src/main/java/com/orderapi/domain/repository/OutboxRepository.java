package com.orderapi.domain.repository;

import com.orderapi.domain.model.OutboxEvent;

import java.util.List;

public interface OutboxRepository {
    void save(OutboxEvent event);
    List<OutboxEvent> findPending(int limit);
    void markPublished(OutboxEvent event);
}
