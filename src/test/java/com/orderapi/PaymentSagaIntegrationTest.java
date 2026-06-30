package com.orderapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderapi.api.dto.CreateOrderRequest;
import com.orderapi.api.dto.OrderItemRequest;
import com.orderapi.api.dto.OrderResponse;
import com.orderapi.domain.event.PaymentResultEvent;
import com.orderapi.domain.model.OrderStatus;
import com.orderapi.infra.kafka.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Demonstra o ciclo do Saga no order-api contra um broker Kafka real:
 * order-api publica {@code orders.created} (via outbox) e consome
 * {@code payments.approved} / {@code payments.failed}, refletindo o resultado
 * no status do pedido.
 */
class PaymentSagaIntegrationTest extends IntegrationTestBase {

    private static final String ORDERS_CREATED = "orders.created";
    private static final String PAYMENTS_APPROVED = "payments.approved";
    private static final String PAYMENTS_FAILED = "payments.failed";

    @Autowired
    private ObjectMapper objectMapper;

    // ========== 1. order-api publica em orders.created ==========

    @Test
    void shouldPublishOrderCreatedToKafka() {
        var orderId = createOrder(new BigDecimal("250.00"));

        var event = awaitOrderCreated(orderId);
        assertThat(event).isNotNull();
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.customerId()).isEqualTo(DEFAULT_USER_ID);
        assertThat(event.totalAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    // ========== 2. payments.approved -> pedido vira PAID ==========

    @Test
    void shouldMarkOrderPaidOnApprovedPayment() {
        var orderId = createOrder(new BigDecimal("250.00"));

        publishPaymentResult(PAYMENTS_APPROVED, orderId, "APPROVED", null);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID));
        assertThat(countOutbox(orderId, "ORDER_PAID")).isEqualTo(1);
    }

    // ========== 3. payments.failed -> pedido vira PAYMENT_FAILED ==========

    @Test
    void shouldMarkOrderFailedOnFailedPayment() {
        var orderId = createOrder(new BigDecimal("9999.00"));

        publishPaymentResult(PAYMENTS_FAILED, orderId, "FAILED", "INSUFFICIENT_FUNDS");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAYMENT_FAILED));
        assertThat(countOutbox(orderId, "ORDER_PAYMENT_FAILED")).isEqualTo(1);
    }

    // ========== 4. Idempotência: resultado duplicado debita/transiciona só uma vez ==========

    @Test
    void shouldBeIdempotentOnDuplicatePaymentResult() {
        var orderId = createOrder(new BigDecimal("250.00"));

        publishPaymentResult(PAYMENTS_APPROVED, orderId, "APPROVED", null);
        publishPaymentResult(PAYMENTS_APPROVED, orderId, "APPROVED", null);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID));

        // O segundo evento encontra o pedido já PAID e é ignorado: um único ORDER_PAID.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).until(() ->
                countOutbox(orderId, "ORDER_PAID") == 1);
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
    }

    // ========== Helpers ==========

    private UUID createOrder(BigDecimal unitPrice) {
        var request = new CreateOrderRequest(
                List.of(new OrderItemRequest(UUID.randomUUID(), "Item", 1, unitPrice)));
        var response = rest.postForEntity("/orders", request, OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private OrderStatus statusOf(UUID orderId) {
        return orderJpa.findById(orderId)
                .map(e -> e.toDomain().getStatus())
                .orElse(null);
    }

    private long countOutbox(UUID orderId, String eventType) {
        return outboxJpa.findAll().stream()
                .map(e -> e.toDomain())
                .filter(e -> e.getAggregateId().equals(orderId))
                .filter(e -> e.getEventType().equals(eventType))
                .count();
    }

    private void publishPaymentResult(String topic, UUID orderId, String status, String reason) {
        try (var producer = new KafkaProducer<String, String>(producerProps())) {
            var payload = objectMapper.writeValueAsString(
                    new PaymentResultEvent(orderId, DEFAULT_USER_ID, status, new BigDecimal("250.00"), reason));
            producer.send(new ProducerRecord<>(topic, orderId.toString(), payload)).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private OrderCreatedEvent awaitOrderCreated(UUID orderId) {
        try (var consumer = new KafkaConsumer<String, String>(consumerProps())) {
            consumer.subscribe(List.of(ORDERS_CREATED));
            var found = new OrderCreatedEvent[1];
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (record.key().equals(orderId.toString())) {
                        try {
                            found[0] = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                return found[0] != null;
            });
            return found[0];
        }
    }

    private Properties producerProps() {
        var props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return props;
    }

    private Properties consumerProps() {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }
}
