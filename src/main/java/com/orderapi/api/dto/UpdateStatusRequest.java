package com.orderapi.api.dto;

import com.orderapi.domain.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull OrderStatus status) {}
