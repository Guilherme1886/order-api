package com.orderapi.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * The order's customer is taken from the authenticated principal (the JWT
 * subject), not from the request body, so a client cannot place an order on
 * behalf of another customer.
 */
public record CreateOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items
) {}
