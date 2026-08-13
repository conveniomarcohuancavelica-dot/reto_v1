package com.reto.auth;

import com.reto.auth.security.JwtService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "MiClaveSecretaSuperSeguraParaElRetoTecnico2026ChangeMe", 3600, "auth-service");

    @Test
    void debeGenerarUnTokenNoVacio() {
        String token = jwtService.generateToken("client", "CLIENT");
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "Un JWT debe tener 3 partes separadas por punto");
    }

    @Test
    void debeExponerElTiempoDeExpiracionConfigurado() {
        assertEquals(3600, jwtService.getExpirationSeconds());
    }
}
