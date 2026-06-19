package com.orderapi;

import com.orderapi.infra.auth.AuthServiceClient;
import com.orderapi.infra.auth.IntrospectionResult;
import com.orderapi.infra.repository.JpaOrderRepository;
import com.orderapi.infra.repository.JpaOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    /** Token the inherited {@link #rest} automatically sends; stubbed as a valid, ACTIVE user. */
    protected static final String DEFAULT_TOKEN = "valid-test-token";
    protected static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orderdb")
                    .withUsername("order")
                    .withPassword("order123");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JpaOrderRepository orderJpa;

    @Autowired
    protected JpaOutboxRepository outboxJpa;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** The auth-service is stubbed so the suite never depends on a running auth-service. */
    @MockitoBean
    protected AuthServiceClient authServiceClient;

    @BeforeEach
    void setUpAuthAndDatabase() {
        // Default: any token is invalid; the well-known test token maps to an ACTIVE user.
        when(authServiceClient.introspect(anyString())).thenReturn(IntrospectionResult.inactive());
        when(authServiceClient.introspect(DEFAULT_TOKEN))
                .thenReturn(new IntrospectionResult(true, DEFAULT_USER_ID, "test@example.com"));

        // Send the valid token on every request that doesn't already set Authorization.
        if (rest.getRestTemplate().getInterceptors().isEmpty()) {
            rest.getRestTemplate().getInterceptors().add((request, body, execution) -> {
                if (!request.getHeaders().containsKey("Authorization")) {
                    request.getHeaders().setBearerAuth(DEFAULT_TOKEN);
                }
                return execution.execute(request, body);
            });
        }

        jdbcTemplate.execute("TRUNCATE TABLE outbox_events, order_items, orders");
    }
}
