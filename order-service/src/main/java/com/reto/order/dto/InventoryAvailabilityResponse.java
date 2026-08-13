package com.reto.order.dto;

public record InventoryAvailabilityResponse(
        String productId,
        String productName,
        Integer stock,
        boolean available
) {}
