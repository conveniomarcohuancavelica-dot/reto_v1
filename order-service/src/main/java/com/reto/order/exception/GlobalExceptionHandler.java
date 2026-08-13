package com.reto.order.exception;

import com.reto.order.dto.ErrorResponse;
import com.reto.order.filter.TraceIdWebFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Traduce cada excepción de negocio al formato de error único exigido por
 * el reto: { timestamp, status, code, message, traceId }.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleNotFound(OrderNotFoundException ex, ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", ex.getMessage(), exchange);
    }

    @ExceptionHandler(StockInsufficientException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleStock(StockInsufficientException ex, ServerWebExchange exchange) {
        return build(HttpStatus.CONFLICT, "STOCK_INSUFFICIENT", ex.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidTransition(InvalidOrderTransitionException ex, ServerWebExchange exchange) {
        return build(HttpStatus.CONFLICT, "INVALID_TRANSITION", ex.getMessage(), exchange);
    }

    @ExceptionHandler(InventoryServiceUnavailableException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInventoryDown(InventoryServiceUnavailableException ex, ServerWebExchange exchange) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "INVENTORY_SERVICE_UNAVAILABLE", ex.getMessage(), exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidation(WebExchangeBindException ex, ServerWebExchange exchange) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Datos inválidos");
        return build(HttpStatus.BAD_REQUEST, "INVALID_DATA", message, exchange);
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneric(Exception ex, ServerWebExchange exchange) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error inesperado", exchange);
    }

    private Mono<ResponseEntity<ErrorResponse>> build(HttpStatus status, String code, String message, ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdWebFilter.TRACE_ID_HEADER);
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), code, message, traceId);
        return Mono.just(ResponseEntity.status(status).body(body));
    }
}
