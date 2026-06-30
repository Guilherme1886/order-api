package com.orderapi.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderapi.domain.event.PaymentResultEvent;
import com.orderapi.domain.model.OrderStatus;
import com.orderapi.domain.model.OutboxEvent;
import com.orderapi.domain.repository.OrderRepository;
import com.orderapi.domain.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passo do Saga que reage ao resultado do pagamento: avança o pedido para
 * {@code PAID} (aprovado) ou {@code PAYMENT_FAILED} (recusado) e enfileira o
 * evento de domínio correspondente no outbox, na mesma transação.
 */
@Service
public class ProcessPaymentResultUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessPaymentResultUseCase.class);

    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_FAILED = "FAILED";

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ProcessPaymentResultUseCase(OrderRepository orderRepository,
                                       OutboxRepository outboxRepository,
                                       ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void execute(PaymentResultEvent event) {
        var order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("Resultado de pagamento para pedido desconhecido {} — ignorando.", event.orderId());
            return;
        }

        // Idempotência: o resultado do pagamento só se aplica enquanto o pedido
        // aguarda pagamento (PENDING). Reentrega do Kafka ou resultado duplicado
        // encontra o pedido já em PAID/PAYMENT_FAILED e é ignorado.
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Pedido {} já processado (status={}) — ignorando resultado de pagamento (idempotência).",
                    order.getId(), order.getStatus());
            return;
        }

        final OrderStatus targetStatus;
        final String eventType;
        if (STATUS_APPROVED.equals(event.status())) {
            targetStatus = OrderStatus.PAID;
            eventType = "ORDER_PAID";
        } else if (STATUS_FAILED.equals(event.status())) {
            targetStatus = OrderStatus.PAYMENT_FAILED;
            eventType = "ORDER_PAYMENT_FAILED";
        } else {
            log.warn("Pedido {}: status de pagamento desconhecido '{}' — ignorando.",
                    order.getId(), event.status());
            return;
        }

        order.transitionTo(targetStatus);
        var saved = orderRepository.save(order);
        outboxRepository.save(OutboxEvent.create(saved.getId(), eventType, toJson(saved)));

        log.info("Pedido {} -> {} (reason={}); evento {} enfileirado no outbox.",
                saved.getId(), targetStatus, event.reason(), eventType);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }
}
