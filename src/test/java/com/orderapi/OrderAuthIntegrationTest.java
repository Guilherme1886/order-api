package com.orderapi;

import com.orderapi.api.dto.CreateOrderRequest;
import com.orderapi.api.dto.OrderItemRequest;
import com.orderapi.api.dto.OrderResponse;
import com.orderapi.infra.auth.IntrospectionResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Exercises the JWT authentication boundary on POST /orders. Uses a raw
 * TestRestTemplate (no auto-injected token) so each test controls the
 * Authorization header explicitly. The auth-service is stubbed via the
 * inherited {@code authServiceClient} mock.
 */
class OrderAuthIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    private final TestRestTemplate rawRest = new TestRestTemplate();

    private CreateOrderRequest sampleOrder() {
        return new CreateOrderRequest(
                List.of(new OrderItemRequest(UUID.randomUUID(), "Keyboard", 1, new BigDecimal("250.00"))));
    }

    private HttpEntity<CreateOrderRequest> withToken(String token) {
        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(sampleOrder(), headers);
    }

    @Test
    void shouldCreateOrderWithValidToken() {
        var userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(authServiceClient.introspect("good-token"))
                .thenReturn(new IntrospectionResult(true, userId, "buyer@example.com"));

        var response = rawRest.postForEntity(
                "http://localhost:" + port + "/orders", withToken("good-token"), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        // the order's customer is the authenticated user resolved from the token
        assertThat(response.getBody().customerId()).isEqualTo(userId);
    }

    @Test
    void shouldRejectWhenNoTokenProvided() {
        var response = rawRest.postForEntity(
                "http://localhost:" + port + "/orders", withToken(null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenTokenIsInvalid() {
        when(authServiceClient.introspect("bad-token")).thenReturn(IntrospectionResult.inactive());

        var response = rawRest.postForEntity(
                "http://localhost:" + port + "/orders", withToken("bad-token"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
