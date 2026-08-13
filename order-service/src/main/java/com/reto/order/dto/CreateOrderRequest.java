package com.reto.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotBlank(message = "customerId es obligatorio") String customerId,
        @NotBlank(message = "productId es obligatorio") String productId,
        @NotNull(message = "quantity es obligatorio") @Min(value = 1, message = "quantity debe ser mayor a 0") Integer quantity
) {}
