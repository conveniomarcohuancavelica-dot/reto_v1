package com.reto.auth.controller;

import com.reto.auth.dto.LoginRequest;
import com.reto.auth.dto.TokenResponse;
import com.reto.auth.security.InMemoryUser;
import com.reto.auth.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * FLUJO "Login" — PASO A: recibe usuario/contraseña y los valida contra
     * la lista en memoria (InMemoryUser). Si son correctos, PASO B: delega
     * en JwtService.generateToken() (siguiente archivo) para firmar el JWT
     * que el cliente va a usar como "Authorization: Bearer {token}" en
     * TODAS las demás peticiones (ese token es lo que valida
     * api-gateway/config/SecurityConfig.java en el PASO 2 del flujo
     * "Crear pedido"). Este endpoint es el único público sin token — está
     * en la lista permitAll() del Gateway.
     *
     * Autentica un usuario de prueba y devuelve un JWT firmado (HS256).
     * Este token debe enviarse como "Authorization: Bearer {token}" en cada
     * request al API Gateway.
     *
     * Usuarios de prueba: client/client123 (rol CLIENT), admin/admin123 (rol ADMIN)
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        InMemoryUser user = InMemoryUser.findByUsername(request.username());

        if (user == null || !user.password().equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorBody("INVALID_CREDENTIALS", "Usuario o contraseña incorrectos"));
        }

        String token = jwtService.generateToken(user.username(), user.role());
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", jwtService.getExpirationSeconds()));
    }

    private record ErrorBody(String code, String message) {}
}
