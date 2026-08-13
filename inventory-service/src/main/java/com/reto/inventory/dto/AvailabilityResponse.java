package com.reto.inventory.dto;

public record AvailabilityResponse(
        String productId,
        String productName,
        Integer stock,
        boolean available
) {}
