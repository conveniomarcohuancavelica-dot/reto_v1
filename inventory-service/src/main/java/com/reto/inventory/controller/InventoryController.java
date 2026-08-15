package com.reto.inventory.controller;

import com.reto.inventory.dto.AvailabilityResponse;
import com.reto.inventory.dto.ReserveStockRequest;
import com.reto.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * [PASO 9 · FLUJO "Crear pedido"] — Recibe el GET que salió de
     * InventoryClient.checkAvailability() (paso 7), ya pasado por el
     * TraceIdWebFilter (paso 8). Delega en el service -> PASO 10.
     */
    @GetMapping("/availability")
    public Mono<AvailabilityResponse> checkAvailability(@RequestParam String productId) {
        return inventoryService.checkAvailability(productId);
    }

    /**
     * [PASO 13 · FLUJO "Crear pedido"] — Recibe el POST que salió de
     * InventoryClient.reserveStock() (paso 12). Esta es la llamada que de
     * verdad descuenta stock -> PASO 14, en InventoryServiceImpl.
     */
    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AvailabilityResponse> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        return inventoryService.reserveStock(request);
    }
}
