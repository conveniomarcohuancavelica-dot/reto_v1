package com.reto.order.exception;

public class InventoryServiceUnavailableException extends RuntimeException {
    public InventoryServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
