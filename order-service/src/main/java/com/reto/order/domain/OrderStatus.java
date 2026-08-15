package com.reto.order.domain;

/**
 * Estados posibles de un pedido y sus transiciones válidas.
 *
 * PENDING     -> pedido recién creado, aún no confirmado.
 * CONFIRMED   -> hay stock, el pedido quedó confirmado.
 * CANCELLED   -> el pedido fue cancelado (solo permitido desde PENDING o CONFIRMED).
 * FAILED      -> no se pudo confirmar por falta de stock (estado terminal, opcional).
 *
 * Centralizar las transiciones válidas aquí evita esparcir "ifs" de reglas
 * de negocio por el código (Single Responsibility) y facilita testear el
 * flujo de estados de forma aislada.
 */
public enum OrderStatus {

    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED;

    /**
     * [PASO 17 · FLUJO "Crear pedido" / "Cancelar pedido"] — La regla de
     * negocio pura, sin efectos secundarios: dado el estado actual (this),
     * ¿es válido pasar a "target"? Se llama desde
     * OrderTransitionWriter.transition() (PASO 16). Si devuelve false, ahí
     * mismo se lanza InvalidOrderTransitionException (409) — así se
     * bloquean tanto transiciones inválidas como cancelaciones duplicadas
     * (CANCELLED -> CANCELLED da false, porque es un estado terminal).
     */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED || target == FAILED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED, FAILED -> false; // estados terminales
        };
    }

    public boolean isCancellable() {
        return this == PENDING || this == CONFIRMED;
    }
}
