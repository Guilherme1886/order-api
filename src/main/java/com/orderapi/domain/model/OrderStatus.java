package com.orderapi.domain.model;

import java.util.Set;

public enum OrderStatus {

    PENDING,
    PAID,
    PAYMENT_FAILED,
    PROCESSING,
    OUT_OF_STOCK,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Set<OrderStatus> TERMINAL = Set.of(DELIVERED, PAYMENT_FAILED, CANCELLED);

    public boolean canTransitionTo(OrderStatus target) {
        if (TERMINAL.contains(this)) return false;
        return switch (this) {
            case PENDING      -> target == PAID || target == PAYMENT_FAILED || target == CANCELLED;
            case PAID         -> target == PROCESSING || target == OUT_OF_STOCK || target == CANCELLED;
            case OUT_OF_STOCK -> target == PROCESSING || target == CANCELLED;
            case PROCESSING   -> target == SHIPPED;
            case SHIPPED      -> target == DELIVERED;
            default           -> false;
        };
    }

    public boolean isCancellable() {
        return this != SHIPPED && this != DELIVERED && !TERMINAL.contains(this);
    }
}
