package com.reto.order.dto;

import com.reto.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String customerId,
        String productId,
        Integer quantity,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
