package com.orderapi.infra.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Validates access tokens by delegating to the auth-service introspection
 * endpoint, instead of sharing the JWT signing secret with this service.
 */
@Component
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private final RestTemplate restTemplate;
    private final String introspectUrl;

    public AuthServiceClient(RestTemplateBuilder builder,
                             @Value("${auth.service.url}") String authServiceUrl) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .build();
        this.introspectUrl = authServiceUrl + "/auth/introspect";
    }

    /**
     * Calls {@code POST {auth.service.url}/auth/introspect} with {@code {"token": token}}.
     * Any transport error or non-2xx response is treated as an inactive token
     * so a failing auth-service can never accidentally authorize a request.
     */
    public IntrospectionResult introspect(String token) {
        try {
            IntrospectionResult result = restTemplate.postForObject(
                    introspectUrl, Map.of("token", token), IntrospectionResult.class);
            return result != null ? result : IntrospectionResult.inactive();
        } catch (RestClientException ex) {
            log.warn("Token introspection failed against {}: {}", introspectUrl, ex.getMessage());
            return IntrospectionResult.inactive();
        }
    }
}
