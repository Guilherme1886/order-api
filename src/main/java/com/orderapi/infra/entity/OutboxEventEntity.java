package com.orderapi.infra.entity;

import com.orderapi.domain.model.OutboxEvent;
import com.orderapi.domain.model.OutboxEvent.OutboxStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {}

    public static OutboxEventEntity fromDomain(OutboxEvent event) {
        var entity = new OutboxEventEntity();
        entity.id = event.getId();
        entity.aggregateId = event.getAggregateId();
        entity.eventType = event.getEventType();
        entity.payload = event.getPayload();
        entity.status = event.getStatus();
        entity.createdAt = event.getCreatedAt();
        entity.publishedAt = event.getPublishedAt();
        return entity;
    }

    public OutboxEvent toDomain() {
        return new OutboxEvent(id, aggregateId, eventType, payload, status, createdAt, publishedAt);
    }

    public void setStatus(OutboxStatus status) { this.status = status; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
