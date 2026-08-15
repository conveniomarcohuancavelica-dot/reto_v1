package com.reto.order.controller;

import com.reto.order.dto.CreateOrderRequest;
import com.reto.order.dto.OrderHistoryResponse;
import com.reto.order.dto.OrderResponse;
import com.reto.order.filter.TraceIdWebFilter;
import com.reto.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * [PASO 3 · FLUJO "Crear pedido"] — El Gateway ya validó el JWT (paso 2)
     * y enrutó hasta aquí. Este método solo delega: no tiene lógica de
     * negocio, la deja toda en el service (siguiente archivo:
     * OrderServiceImpl.java, método createOrder()).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request, ServerWebExchange exchange) {
        return orderService.createOrder(request, traceId(exchange));
    }

    @GetMapping("/{orderId}")
    public Mono<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return orderService.getOrder(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public Mono<OrderResponse> cancelOrder(@PathVariable UUID orderId, ServerWebExchange exchange) {
        return orderService.cancelOrder(orderId, traceId(exchange));
    }

    @GetMapping("/{orderId}/history")
    public Flux<OrderHistoryResponse> getHistory(@PathVariable UUID orderId) {
        return orderService.getHistory(orderId);
    }

    private String traceId(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(TraceIdWebFilter.TRACE_ID_HEADER);
    }
}
