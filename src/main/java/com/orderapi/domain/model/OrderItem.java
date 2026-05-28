package com.orderapi.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItem(
        UUID id,
        UUID orderId,
        UUID productId,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
