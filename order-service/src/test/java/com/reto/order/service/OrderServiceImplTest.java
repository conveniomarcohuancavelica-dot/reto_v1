package com.reto.order.service;

import com.reto.order.client.InventoryClient;
import com.reto.order.domain.Order;
import com.reto.order.domain.OrderHistory;
import com.reto.order.domain.OrderStatus;
import com.reto.order.dto.CreateOrderRequest;
import com.reto.order.dto.InventoryAvailabilityResponse;
import com.reto.order.exception.InvalidOrderTransitionException;
import com.reto.order.exception.OrderNotFoundException;
import com.reto.order.repository.OrderHistoryRepository;
import com.reto.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderHistoryRepository historyRepository;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private OrderTransitionWriter transitionWriter;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order pendingOrder;
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pendingOrder = Order.builder()
                .id(orderId)
                .customerId("CUST-1")
                .productId("PROD-001")
                .quantity(2)
                .status(OrderStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Order withStatus(Order source, OrderStatus status) {
        return Order.builder()
                .id(source.getId())
                .customerId(source.getCustomerId())
                .productId(source.getProductId())
                .quantity(source.getQuantity())
                .status(status)
                .createdAt(source.getCreatedAt())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createOrder_deberiaConfirmarCuandoHayStockSuficiente() {
        CreateOrderRequest request = new CreateOrderRequest("CUST-1", "PROD-001", 2);
        when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);
        when(inventoryClient.checkAvailability("PROD-001"))
                .thenReturn(Mono.just(new InventoryAvailabilityResponse("PROD-001", "Laptop", 10, true)));
        when(inventoryClient.reserveStock("PROD-001", 2))
                .thenReturn(Mono.just(new InventoryAvailabilityResponse("PROD-001", "Laptop", 8, true)));
        when(transitionWriter.transition(any(Order.class), eq(OrderStatus.CONFIRMED), anyString(), anyString()))
                .thenReturn(withStatus(pendingOrder, OrderStatus.CONFIRMED));

        StepVerifier.create(orderService.createOrder(request, "trace-123"))
                .expectNextMatches(response -> response.status() == OrderStatus.CONFIRMED)
                .verifyComplete();

        verify(transitionWriter, times(1))
                .transition(any(Order.class), eq(OrderStatus.CONFIRMED), anyString(), anyString());
    }

    @Test
    void createOrder_deberiaMarcarFailedCuandoNoHayStock() {
        CreateOrderRequest request = new CreateOrderRequest("CUST-1", "PROD-001", 5);
        when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);
        when(inventoryClient.checkAvailability("PROD-001"))
                .thenReturn(Mono.just(new InventoryAvailabilityResponse("PROD-001", "Laptop", 1, true)));
        when(transitionWriter.transition(any(Order.class), eq(OrderStatus.FAILED), anyString(), anyString()))
                .thenReturn(withStatus(pendingOrder, OrderStatus.FAILED));

        StepVerifier.create(orderService.createOrder(request, "trace-123"))
                .expectNextMatches(response -> response.status() == OrderStatus.FAILED)
                .verifyComplete();

        verify(inventoryClient, never()).reserveStock(any(), anyInt());
    }

    @Test
    void cancelOrder_deberiaCancelarUnPedidoPendiente() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
        when(transitionWriter.transition(any(Order.class), eq(OrderStatus.CANCELLED), anyString(), anyString()))
                .thenReturn(withStatus(pendingOrder, OrderStatus.CANCELLED));

        StepVerifier.create(orderService.cancelOrder(orderId, "trace-123"))
                .expectNextMatches(response -> response.status() == OrderStatus.CANCELLED)
                .verifyComplete();
    }

    @Test
    void cancelOrder_noDeberiaPermitirCancelarUnPedidoYaCancelado() {
        pendingOrder.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
        when(transitionWriter.transition(any(Order.class), eq(OrderStatus.CANCELLED), anyString(), anyString()))
                .thenThrow(new InvalidOrderTransitionException(orderId, OrderStatus.CANCELLED, OrderStatus.CANCELLED));

        StepVerifier.create(orderService.cancelOrder(orderId, "trace-123"))
                .expectError(InvalidOrderTransitionException.class)
                .verify();

        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrder_deberiaLanzarExcepcionCuandoNoExiste() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        StepVerifier.create(orderService.getOrder(orderId))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void createOrder_deberiaLanzarStockInsufficientComoErrorControladoInternamente() {
        // Verifica que aunque checkAvailability indique stock 0, el flujo no
        // propaga StockInsufficientException al llamador final: se traduce
        // a un pedido en estado FAILED con historial (manejo de error controlado).
        CreateOrderRequest request = new CreateOrderRequest("CUST-1", "PROD-003", 1);
        Order zeroStockOrder = Order.builder()
                .id(orderId).customerId("CUST-1").productId("PROD-003")
                .quantity(1).status(OrderStatus.PENDING).build();
        when(orderRepository.save(any(Order.class))).thenReturn(zeroStockOrder);
        when(inventoryClient.checkAvailability("PROD-003"))
                .thenReturn(Mono.just(new InventoryAvailabilityResponse("PROD-003", "Teclado", 0, false)));
        when(transitionWriter.transition(any(Order.class), eq(OrderStatus.FAILED), anyString(), anyString()))
                .thenReturn(withStatus(zeroStockOrder, OrderStatus.FAILED));

        StepVerifier.create(orderService.createOrder(request, "trace-999"))
                .expectNextMatches(r -> r.status() == OrderStatus.FAILED)
                .verifyComplete();
    }

    @Test
    void getHistory_deberiaMapearElHistorialDelPedidoEnOrden() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now();
        OrderHistory h1 = OrderHistory.builder()
                .orderId(orderId).previousStatus(null).newStatus(OrderStatus.PENDING)
                .reason("Pedido creado").changedAt(t1).traceId("trace-a").build();
        OrderHistory h2 = OrderHistory.builder()
                .orderId(orderId).previousStatus(OrderStatus.PENDING).newStatus(OrderStatus.CONFIRMED)
                .reason("Stock disponible").changedAt(t2).traceId("trace-b").build();
        when(historyRepository.findByOrderIdOrderByChangedAtAsc(orderId)).thenReturn(List.of(h1, h2));

        StepVerifier.create(orderService.getHistory(orderId))
                .expectNextMatches(r -> r.newStatus() == OrderStatus.PENDING && r.previousStatus() == null)
                .expectNextMatches(r -> r.newStatus() == OrderStatus.CONFIRMED && r.previousStatus() == OrderStatus.PENDING)
                .verifyComplete();

        verify(historyRepository, times(1)).findByOrderIdOrderByChangedAtAsc(orderId);
    }

    @Test
    void getHistory_deberiaLanzarExcepcionCuandoElPedidoNoExiste() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        StepVerifier.create(orderService.getHistory(orderId))
                .expectError(OrderNotFoundException.class)
                .verify();

        verify(historyRepository, never()).findByOrderIdOrderByChangedAtAsc(any());
    }
}
