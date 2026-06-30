package com.orderapi.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderapi.application.usecase.ProcessPaymentResultUseCase;
import com.orderapi.domain.event.PaymentResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adapter de entrada do Saga: escuta os resultados de pagamento e delega ao
 * {@link ProcessPaymentResultUseCase}. Os dois tópicos compartilham um único
 * listener pois carregam o mesmo contrato ({@link PaymentResultEvent}).
 */
@Component
@ConditionalOnProperty(prefix = "kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final ProcessPaymentResultUseCase processPaymentResultUseCase;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(ProcessPaymentResultUseCase processPaymentResultUseCase,
                                ObjectMapper objectMapper) {
        this.processPaymentResultUseCase = processPaymentResultUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"payments.approved", "payments.failed"},
            groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentResult(String message) {
        try {
            var event = objectMapper.readValue(message, PaymentResultEvent.class);
            log.info("Recebido resultado de pagamento: orderId={}, status={}",
                    event.orderId(), event.status());
            processPaymentResultUseCase.execute(event);
        } catch (Exception e) {
            log.error("Falha ao processar resultado de pagamento: {}", message, e);
        }
    }
}
