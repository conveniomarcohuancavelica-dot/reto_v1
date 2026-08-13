package com.reto.inventory.service;

import com.reto.inventory.dto.AvailabilityResponse;
import com.reto.inventory.dto.ReserveStockRequest;
import reactor.core.publisher.Mono;

/**
 * Contrato del servicio de inventario. Se define como interfaz (principio
 * de inversión de dependencias - SOLID) para desacoplar el controlador de
 * la implementación concreta y facilitar el mockeo en pruebas.
 */
public interface InventoryService {

    Mono<AvailabilityResponse> checkAvailability(String productId);

    /**
     * Descuenta stock de forma atómica. Si no hay stock suficiente lanza
     * InsufficientStockException; si el producto no existe, ProductNotFoundException.
     */
    Mono<AvailabilityResponse> reserveStock(ReserveStockRequest request);
}
