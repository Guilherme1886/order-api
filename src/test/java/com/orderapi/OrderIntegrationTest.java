package com.orderapi;

import com.orderapi.api.dto.CreateOrderRequest;
import com.orderapi.api.dto.OrderItemRequest;
import com.orderapi.api.dto.OrderResponse;
import com.orderapi.api.dto.UpdateStatusRequest;
import com.orderapi.domain.model.OrderStatus;
import com.orderapi.domain.model.OutboxEvent.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OrderIntegrationTest extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    // ========== 1. Criação de pedido — atomicidade com Outbox ==========

    @Test
    void shouldCreateOrderWithOutboxEventAtomically() {
        var request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(
                        new OrderItemRequest(UUID.randomUUID(), "Notebook", 2, new BigDecimal("3500.00")),
                        new OrderItemRequest(UUID.randomUUID(), "Mouse", 1, new BigDecimal("120.00"))
                ));

        var response = rest.postForEntity("/orders", request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(body.totalAmount()).isEqualByComparingTo(new BigDecimal("7120.00"));
        assertThat(body.items()).hasSize(2);

        var orderInDb = orderJpa.findById(body.id());
        assertThat(orderInDb).isPresent();
        assertThat(orderInDb.get().getStatus()).isEqualTo(OrderStatus.PENDING);

        var outboxEvents = outboxJpa.findAll().stream()
                .filter(e -> e.toDomain().getAggregateId().equals(body.id()))
                .toList();
        assertThat(outboxEvents).hasSize(1);
        var event = outboxEvents.getFirst().toDomain();
        assertThat(event.getEventType()).isEqualTo("ORDER_CREATED");
    }

    // ========== 2. Transição de status + outbox atômico ==========

    @Test
    void shouldTransitionStatusAndCreateOutboxEventAtomically() {
        var orderId = createPendingOrder();

        var patchResponse = rest.exchange(
                "/orders/" + orderId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(new UpdateStatusRequest(OrderStatus.PAID)),
                OrderResponse.class);

        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResponse.getBody().status()).isEqualTo(OrderStatus.PAID);

        var orderInDb = orderJpa.findById(orderId);
        assertThat(orderInDb).isPresent();
        assertThat(orderInDb.get().getStatus()).isEqualTo(OrderStatus.PAID);

        var outboxEvents = outboxJpa.findAll().stream()
                .map(e -> e.toDomain())
                .filter(e -> e.getAggregateId().equals(orderId))
                .toList();
        assertThat(outboxEvents).hasSize(2);

        var statusEvent = outboxEvents.stream()
                .filter(e -> e.getEventType().equals("ORDER_STATUS_CHANGED_TO_PAID"))
                .findFirst();
        assertThat(statusEvent).isPresent();
        assertThat(statusEvent.get().getAggregateId()).isEqualTo(orderId);
    }

    // ========== 3. Cancelamento inválido — SHIPPED não pode cancelar ==========

    @Test
    void shouldRejectCancellationOfShippedOrder() {
        var orderId = createPendingOrder();
        patchStatus(orderId, OrderStatus.PAID);
        patchStatus(orderId, OrderStatus.PROCESSING);
        patchStatus(orderId, OrderStatus.SHIPPED);

        var eventsBeforeCancel = outboxJpa.findAll().size();

        var cancelResponse = rest.exchange(
                "/orders/" + orderId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(new UpdateStatusRequest(OrderStatus.CANCELLED)),
                String.class);

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(cancelResponse.getBody()).contains("cannot be cancelled in status SHIPPED");

        var orderInDb = orderJpa.findById(orderId);
        assertThat(orderInDb).isPresent();
        assertThat(orderInDb.get().getStatus()).isEqualTo(OrderStatus.SHIPPED);

        var eventsAfterCancel = outboxJpa.findAll().size();
        assertThat(eventsAfterCancel).isEqualTo(eventsBeforeCancel);

        var cancelEvents = outboxJpa.findAll().stream()
                .filter(e -> e.toDomain().getEventType().contains("CANCELLED"))
                .toList();
        assertThat(cancelEvents).isEmpty();
    }

    // ========== 4. Paginação e filtro por status ==========

    @Test
    void shouldFilterByStatusAndPaginate() {
        for (int i = 0; i < 3; i++) {
            createPendingOrder();
        }
        var paid1 = createPendingOrder();
        var paid2 = createPendingOrder();
        patchStatus(paid1, OrderStatus.PAID);
        patchStatus(paid2, OrderStatus.PAID);

        var filteredResponse = rest.getForEntity(
                "/orders?status=PAID&page=0&size=10", String.class);
        assertThat(filteredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var filteredBody = filteredResponse.getBody();
        assertThat(filteredBody).contains("\"totalElements\":2");
        assertThat(filteredBody).doesNotContain("\"status\":\"PENDING\"");

        var paginatedResponse = rest.getForEntity(
                "/orders?page=0&size=2", String.class);
        assertThat(paginatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var paginatedBody = paginatedResponse.getBody();
        assertThat(paginatedBody).contains("\"numberOfElements\":2");
        assertThat(paginatedBody).contains("\"totalElements\":5");
    }

    // ========== 5. OutboxPublisher processa eventos pendentes ==========

    @Test
    void shouldPublishPendingOutboxEvents() {
        var orderId = createPendingOrder();

        var pendingBefore = outboxJpa.findByStatus(OutboxStatus.PENDING, PageRequest.of(0, 100));
        assertThat(pendingBefore).isNotEmpty();
        assertThat(pendingBefore.stream().anyMatch(
                e -> e.toDomain().getAggregateId().equals(orderId))).isTrue();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var pending = outboxJpa.findByStatus(OutboxStatus.PENDING, PageRequest.of(0, 100))
                    .stream()
                    .filter(e -> e.toDomain().getAggregateId().equals(orderId))
                    .toList();
            assertThat(pending).isEmpty();
        });

        var published = outboxJpa.findByStatus(OutboxStatus.PUBLISHED, PageRequest.of(0, 100))
                .stream()
                .filter(e -> e.toDomain().getAggregateId().equals(orderId))
                .toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().toDomain().getPublishedAt()).isNotNull();
    }

    // ========== Helpers ==========

    private UUID createPendingOrder() {
        var request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(
                        UUID.randomUUID(), "Item", 1, new BigDecimal("100.00"))));
        var response = rest.postForEntity("/orders", request, OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private void patchStatus(UUID orderId, OrderStatus status) {
        var response = rest.exchange(
                "/orders/" + orderId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(new UpdateStatusRequest(status)),
                OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
