package com.reto.order.exception;

import com.reto.order.domain.OrderStatus;

import java.util.UUID;

public class InvalidOrderTransitionException extends RuntimeException {
    public InvalidOrderTransitionException(UUID orderId, OrderStatus from, OrderStatus to) {
        super("Transición inválida para el pedido " + orderId + ": de " + from + " a " + to);
    }
}
