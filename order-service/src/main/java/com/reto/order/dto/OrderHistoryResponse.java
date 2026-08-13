package com.reto.order.dto;

import com.reto.order.domain.OrderStatus;

import java.time.Instant;

public record OrderHistoryResponse(
        OrderStatus previousStatus,
        OrderStatus newStatus,
        String reason,
        Instant changedAt
) {}
