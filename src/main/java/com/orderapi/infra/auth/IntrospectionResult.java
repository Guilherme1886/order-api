package com.orderapi.infra.auth;

import java.util.UUID;

/**
 * Response of the auth-service {@code POST /auth/introspect} endpoint.
 * {@code active} is the only field guaranteed to be present; {@code userId}
 * and {@code email} are populated only when the token is valid.
 */
public record IntrospectionResult(boolean active, UUID userId, String email) {

    public static IntrospectionResult inactive() {
        return new IntrospectionResult(false, null, null);
    }
}
