package com.reto.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReserveStockRequest(
        @NotBlank(message = "productId es obligatorio") String productId,
        @NotNull(message = "quantity es obligatorio") @Min(value = 1, message = "quantity debe ser mayor a 0") Integer quantity
) {}
