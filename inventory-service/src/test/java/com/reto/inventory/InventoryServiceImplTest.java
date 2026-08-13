package com.reto.inventory;

import com.reto.inventory.domain.InventoryItem;
import com.reto.inventory.dto.AvailabilityResponse;
import com.reto.inventory.dto.ReserveStockRequest;
import com.reto.inventory.exception.InsufficientStockException;
import com.reto.inventory.exception.ProductNotFoundException;
import com.reto.inventory.repository.InventoryItemRepository;
import com.reto.inventory.service.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryItemRepository repository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private InventoryItem item;

    @BeforeEach
    void setUp() {
        item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .productId("PROD-001")
                .productName("Laptop 14 pulgadas")
                .stock(10)
                .build();
    }

    @Test
    void checkAvailability_deberiaRetornarDisponibleCuandoHayStock() {
        when(repository.findByProductId("PROD-001")).thenReturn(Optional.of(item));

        StepVerifier.create(inventoryService.checkAvailability("PROD-001"))
                .expectNextMatches(AvailabilityResponse::available)
                .verifyComplete();
    }

    @Test
    void checkAvailability_deberiaLanzarExcepcionCuandoProductoNoExiste() {
        when(repository.findByProductId("PROD-999")).thenReturn(Optional.empty());

        StepVerifier.create(inventoryService.checkAvailability("PROD-999"))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void reserveStock_deberiaDescontarStockCuandoHaySuficiente() {
        when(repository.findByProductId("PROD-001")).thenReturn(Optional.of(item));
        when(repository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        StepVerifier.create(inventoryService.reserveStock(new ReserveStockRequest("PROD-001", 3)))
                .expectNextMatches(response -> response.stock() == 7)
                .verifyComplete();

        verify(repository, times(1)).save(any(InventoryItem.class));
    }

    @Test
    void reserveStock_deberiaLanzarExcepcionCuandoStockEsInsuficiente() {
        when(repository.findByProductId("PROD-001")).thenReturn(Optional.of(item));

        StepVerifier.create(inventoryService.reserveStock(new ReserveStockRequest("PROD-001", 999)))
                .expectError(InsufficientStockException.class)
                .verify();

        verify(repository, never()).save(any());
    }
}
