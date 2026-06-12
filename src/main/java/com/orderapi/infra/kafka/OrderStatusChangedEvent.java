package com.orderapi.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.orderapi.domain.model.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload published to the {@code orders.status.changed} topic.
 * The outbox stores the full serialized order; unknown fields are ignored
 * so the same record can be read from that richer JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderStatusChangedEvent(
        UUID id,
        OrderStatus status,
        UUID customerId,
        Instant updatedAt
) {}
