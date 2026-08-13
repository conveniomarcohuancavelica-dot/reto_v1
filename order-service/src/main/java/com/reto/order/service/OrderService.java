package com.reto.order.service;

import com.reto.order.dto.CreateOrderRequest;
import com.reto.order.dto.OrderHistoryResponse;
import com.reto.order.dto.OrderResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Contrato del servicio de pedidos. Definido como interfaz para desacoplar
 * el controlador de la implementación (Dependency Inversion - SOLID) y
 * facilitar el mockeo en pruebas unitarias.
 */
public interface OrderService {

    Mono<OrderResponse> createOrder(CreateOrderRequest request, String traceId);

    Mono<OrderResponse> getOrder(UUID orderId);

    Mono<OrderResponse> cancelOrder(UUID orderId, String traceId);

    Flux<OrderHistoryResponse> getHistory(UUID orderId);
}
