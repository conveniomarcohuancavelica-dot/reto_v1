package com.reto.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final String issuer;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-seconds:3600}") long expirationSeconds,
            @Value("${security.jwt.issuer:auth-service}") String issuer) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
    }

    /**
     * FLUJO "Login" — último paso: construye y firma el JWT con la
     * librería jjwt. La respuesta (el token) vuelve a AuthController, que
     * la devuelve al cliente. Esta es la MISMA clave secreta
     * (security.jwt.secret) que usa api-gateway/config/SecurityConfig.java
     * para verificar la firma — por eso el token que emite auth-service es
     * aceptado por el Gateway sin que ambos servicios se conozcan entre sí
     * más allá de compartir ese secreto por configuración.
     */
    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claims(Map.of("role", role, "scope", role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
