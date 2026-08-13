package com.reto.order.service;

import com.reto.order.client.InventoryClient;
import com.reto.order.domain.Order;
import com.reto.order.domain.OrderHistory;
import com.reto.order.domain.OrderStatus;
import com.reto.order.dto.*;
import com.reto.order.exception.InvalidOrderTransitionException;
import com.reto.order.exception.OrderNotFoundException;
import com.reto.order.exception.StockInsufficientException;
import com.reto.order.repository.OrderHistoryRepository;
import com.reto.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository historyRepository;
    private final InventoryClient inventoryClient;

    @Override
    public Mono<OrderResponse> createOrder(CreateOrderRequest request, String traceId) {
        // 1) Se registra el pedido en PENDING (persistencia propia de Order Service).
        // 2) Se consulta disponibilidad en Inventory Service (llamada reactiva, WebClient).
        // 3) Según el resultado, se confirma o se marca como fallido, dejando rastro en el historial.
        return persistPendingOrder(request)
                .flatMap(order -> inventoryClient.checkAvailability(order.getProductId())
                        .flatMap(availability -> confirmOrIndicateStock(order, availability, request.quantity(), traceId))
                        .onErrorResume(StockInsufficientException.class,
                                ex -> markAsFailed(order, ex.getMessage(), traceId))
                )
                .map(this::toResponse);
    }

    private Mono<Order> persistPendingOrder(CreateOrderRequest request) {
        return Mono.fromCallable(() -> {
                    Order order = Order.builder()
                            .customerId(request.customerId())
                            .productId(request.productId())
                            .quantity(request.quantity())
                            .status(OrderStatus.PENDING)
                            .build();
                    return orderRepository.save(order);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(o -> log.info("Pedido {} creado en estado PENDING", o.getId()));
    }

    private Mono<Order> confirmOrIndicateStock(Order order, InventoryAvailabilityResponse availability,
                                                int requestedQuantity, String traceId) {
        if (!availability.available() || availability.stock() < requestedQuantity) {
            return Mono.error(new StockInsufficientException(order.getProductId()));
        }
        return inventoryClient.reserveStock(order.getProductId(), requestedQuantity)
                .flatMap(reserved -> transitionAndSave(order, OrderStatus.CONFIRMED, "Stock disponible y reservado", traceId));
    }

    private Mono<Order> markAsFailed(Order order, String reason, String traceId) {
        return transitionAndSave(order, OrderStatus.FAILED, reason, traceId);
    }

    private Mono<Order> transitionAndSave(Order order, OrderStatus target, String reason, String traceId) {
        return Mono.fromCallable(() -> {
                    OrderStatus previous = order.getStatus();
                    if (!previous.canTransitionTo(target)) {
                        throw new InvalidOrderTransitionException(order.getId(), previous, target);
                    }
                    order.setStatus(target);
                    Order saved = orderRepository.save(order);
                    saveHistory(saved.getId(), previous, target, reason, traceId);
                    return saved;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<OrderResponse> getOrder(UUID orderId) {
        return findOrderOrFail(orderId).map(this::toResponse);
    }

    @Override
    public Mono<OrderResponse> cancelOrder(UUID orderId, String traceId) {
        return findOrderOrFail(orderId)
                .flatMap(order -> transitionAndSave(order, OrderStatus.CANCELLED, "Cancelado por el cliente", traceId))
                .map(this::toResponse);
    }

    @Override
    public Flux<OrderHistoryResponse> getHistory(UUID orderId) {
        return findOrderOrFail(orderId)
                .flatMapMany(order -> Mono.fromCallable(() ->
                                historyRepository.findByOrderIdOrderByChangedAtAsc(orderId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .map(h -> new OrderHistoryResponse(h.getPreviousStatus(), h.getNewStatus(), h.getReason(), h.getChangedAt()));
    }

    private Mono<Order> findOrderOrFail(UUID orderId) {
        return Mono.fromCallable(() -> orderRepository.findById(orderId)
                        .orElseThrow(() -> new OrderNotFoundException(orderId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    protected void saveHistory(UUID orderId, OrderStatus previous, OrderStatus target, String reason, String traceId) {
        historyRepository.save(OrderHistory.builder()
                .orderId(orderId)
                .previousStatus(previous)
                .newStatus(target)
                .reason(reason)
                .traceId(traceId)
                .build());
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
