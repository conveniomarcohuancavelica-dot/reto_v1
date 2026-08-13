package com.reto.auth.security;

/**
 * Usuarios de prueba embebidos para simplificar la autenticación en el reto técnico.
 * En un escenario real esto vendría de una base de datos de usuarios (tabla propia
 * o un Identity Provider externo como Keycloak).
 */
public record InMemoryUser(String username, String password, String role) {

    public static final InMemoryUser CLIENT = new InMemoryUser("client", "client123", "CLIENT");
    public static final InMemoryUser ADMIN = new InMemoryUser("admin", "admin123", "ADMIN");

    public static InMemoryUser findByUsername(String username) {
        if (CLIENT.username().equals(username)) return CLIENT;
        if (ADMIN.username().equals(username)) return ADMIN;
        return null;
    }
}
