package com.reto.inventory.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productId, int requested, int available) {
        super("Stock insuficiente para el producto " + productId + ". Solicitado: " + requested + ", disponible: " + available);
    }
}
