package com.orderapi.infra.kafka;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload publicado no tópico {@code orders.created} (consumido pelo payment-api).
 * Projeção enxuta do pedido com apenas o que o passo de pagamento precisa.
 */
public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount
) {}
