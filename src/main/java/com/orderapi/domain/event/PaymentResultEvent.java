package com.orderapi.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado do passo de pagamento do Saga, consumido dos tópicos
 * {@code payments.approved} / {@code payments.failed} (publicado pelo payment-api).
 * O {@code reason} só vem preenchido em falhas (ex.: INSUFFICIENT_FUNDS).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResultEvent(
        UUID orderId,
        UUID customerId,
        String status,
        BigDecimal amount,
        String reason
) {}
