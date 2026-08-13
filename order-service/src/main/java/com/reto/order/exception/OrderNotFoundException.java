package com.reto.order.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("No existe el pedido con id: " + orderId);
    }
}
