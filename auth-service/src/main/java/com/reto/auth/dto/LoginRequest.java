package com.reto.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "username es requerido") String username,
        @NotBlank(message = "password es requerido") String password
) {}
