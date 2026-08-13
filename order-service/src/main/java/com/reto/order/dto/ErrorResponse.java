package com.reto.order.dto;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String traceId
) {}
