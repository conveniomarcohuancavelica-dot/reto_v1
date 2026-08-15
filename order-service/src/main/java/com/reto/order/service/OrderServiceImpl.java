package com.reto.order.service;

import com.reto.order.client.InventoryClient;
import com.reto.order.domain.Order;
import com.reto.order.domain.OrderStatus;
import com.reto.order.dto.*;
import com.reto.order.exception.OrderNotFoundException;
import com.reto.order.exception.StockInsufficientException;
import com.reto.order.repository.OrderHistoryRepository;
import com.reto.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
    private final OrderTransitionWriter transitionWriter;

    /**
     * [PASO 4 · FLUJO "Crear pedido"] — Aquí llegamos desde OrderController
     * (paso 3). Este método orquesta TODO el flujo de negocio encadenando
     * llamadas reactivas (.flatMap). Léelo en orden:
     *   1) persistPendingOrder()      -> PASO 5, más abajo en este archivo
     *   2) inventoryClient.checkAvailability() -> PASO 7 (sale de este servicio)
     *   3) confirmOrIndicateStock()   -> más abajo en este archivo, decide CONFIRMED o FAILED
     */
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

    /**
     * [PASO 5 · FLUJO "Crear pedido"] — Guarda el pedido en estado PENDING
     * antes de saber si hay stock (así queda registrado el intento aunque
     * después falle). Al terminar, el flujo vuelve a createOrder() (arriba)
     * y sigue con inventoryClient.checkAvailability() -> PASO 7.
     */
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

    /**
     * [PASO 11 · FLUJO "Crear pedido"] — Volvemos a order-service después
     * de que inventory-service respondió la disponibilidad (paso 10). Si NO
     * hay stock suficiente, esto lanza StockInsufficientException, que
     * createOrder() (arriba) atrapa con .onErrorResume() y desvía hacia
     * markAsFailed() -> mismo PASO 15 pero con destino FAILED en vez de
     * CONFIRMED (ver "FLUJO Crear pedido SIN stock" en la guía HTML).
     * Si SÍ hay stock, llama a inventoryClient.reserveStock() -> PASO 12.
     */
    private Mono<Order> confirmOrIndicateStock(Order order, InventoryAvailabilityResponse availability,
                                                int requestedQuantity, String traceId) {
        if (!availability.available() || availability.stock() < requestedQuantity) {
            return Mono.error(new StockInsufficientException(order.getProductId()));
        }
        return inventoryClient.reserveStock(order.getProductId(), requestedQuantity)
                .flatMap(reserved -> transitionAndSave(order, OrderStatus.CONFIRMED, "Stock disponible y reservado", traceId));
    }

    // FLUJO "Crear pedido SIN stock" — PASO equivalente al 11, pero cuando
    // confirmOrIndicateStock() lanzó el error de arriba. Termina en el mismo
    // transitionAndSave() de abajo, con target=FAILED en vez de CONFIRMED.
    private Mono<Order> markAsFailed(Order order, String reason, String traceId) {
        return transitionAndSave(order, OrderStatus.FAILED, reason, traceId);
    }

    /**
     * [PASO 15 · FLUJO "Crear pedido"] — Punto común al que llegan tanto el
     * camino feliz (CONFIRMED, desde confirmOrIndicateStock) como el fallido
     * (FAILED, desde markAsFailed) y también cancelOrder() más abajo (con
     * CANCELLED). Delega TODO el trabajo de validar+guardar en un bean
     * aparte -> siguiente archivo: OrderTransitionWriter.java, PASO 16.
     */
    private Mono<Order> transitionAndSave(Order order, OrderStatus target, String reason, String traceId) {
        return Mono.fromCallable(() -> transitionWriter.transition(order, target, reason, traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * FLUJO "Consultar estado" — un solo paso: busca y mapea. No toca
     * inventory-service para nada.
     */
    @Override
    public Mono<OrderResponse> getOrder(UUID orderId) {
        return findOrderOrFail(orderId).map(this::toResponse);
    }

    /**
     * FLUJO "Cancelar pedido" — PASO A: busca el pedido (findOrderOrFail,
     * más abajo), PASO B: reutiliza transitionAndSave() (el mismo PASO 15
     * del flujo "Crear pedido", con target=CANCELLED) que a su vez llama a
     * OrderTransitionWriter.transition(), donde se valida con
     * OrderStatus.canTransitionTo() si la cancelación es válida.
     */
    @Override
    public Mono<OrderResponse> cancelOrder(UUID orderId, String traceId) {
        return findOrderOrFail(orderId)
                .flatMap(order -> transitionAndSave(order, OrderStatus.CANCELLED, "Cancelado por el cliente", traceId))
                .map(this::toResponse);
    }

    /**
     * FLUJO "Consultar historial" — busca el pedido, luego trae todas sus
     * filas de la tabla order_history ordenadas por fecha. Es el único
     * lugar donde se usa historyRepository directamente desde este service
     * (el otro uso está encapsulado en OrderTransitionWriter).
     */
    @Override
    public Flux<OrderHistoryResponse> getHistory(UUID orderId) {
        return findOrderOrFail(orderId)
                .flatMapMany(order -> Mono.fromCallable(() ->
                                historyRepository.findByOrderIdOrderByChangedAtAsc(orderId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .map(h -> new OrderHistoryResponse(h.getPreviousStatus(), h.getNewStatus(), h.getReason(), h.getChangedAt()));
    }

    // Usado por getOrder(), cancelOrder() y getHistory() — busca el pedido
    // por id o lanza OrderNotFoundException (-> 404 con el formato de error
    // uniforme, manejado por GlobalExceptionHandler.java).
    private Mono<Order> findOrderOrFail(UUID orderId) {
        return Mono.fromCallable(() -> orderRepository.findById(orderId)
                        .orElseThrow(() -> new OrderNotFoundException(orderId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * [PASO 18 · FLUJO "Crear pedido"] — Último paso dentro de order-service.
     * Convierte la entidad JPA (Order) en el DTO público (OrderResponse) que
     * ve el cliente. De aquí la respuesta sube de vuelta por
     * OrderController -> Gateway -> Postman con status 201.
     */
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
