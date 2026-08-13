package com.reto.order.exception;

public class StockInsufficientException extends RuntimeException {
    public StockInsufficientException(String productId) {
        super("Stock insuficiente para el producto: " + productId);
    }
}
