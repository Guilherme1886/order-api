package com.orderapi.domain.model;

import java.time.Instant;
import java.util.UUID;

public final class OutboxEvent {

    private final UUID id;
    private final UUID aggregateId;
    private final String eventType;
    private final String payload;
    private OutboxStatus status;
    private final Instant createdAt;
    private Instant publishedAt;

    public OutboxEvent(UUID id, UUID aggregateId, String eventType, String payload,
                       OutboxStatus status, Instant createdAt, Instant publishedAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    public static OutboxEvent create(UUID aggregateId, String eventType, String payload) {
        return new OutboxEvent(
                UUID.randomUUID(), aggregateId, eventType, payload,
                OutboxStatus.PENDING, Instant.now(), null
        );
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public enum OutboxStatus { PENDING, PUBLISHED }
}
