package com.reto.inventory.service;

import com.reto.inventory.domain.InventoryItem;
import com.reto.inventory.dto.AvailabilityResponse;
import com.reto.inventory.dto.ReserveStockRequest;
import com.reto.inventory.exception.InsufficientStockException;
import com.reto.inventory.exception.ProductNotFoundException;
import com.reto.inventory.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository repository;

    /**
     * [PASO 10 · FLUJO "Crear pedido"] — Consulta el stock actual y responde.
     * Esta respuesta viaja de vuelta: InventoryController -> por la red ->
     * InventoryClient.checkAvailability (order-service) -> se resuelve el
     * .flatMap del PASO 7 -> sigue en OrderServiceImpl.confirmOrIndicateStock,
     * PASO 11.
     */
    @Override
    public Mono<AvailabilityResponse> checkAvailability(String productId) {
        return Mono.fromCallable(() -> repository.findByProductId(productId)
                        .orElseThrow(() -> new ProductNotFoundException(productId)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toAvailabilityResponse)
                .doOnSuccess(r -> log.info("Consulta de disponibilidad para producto {}: stock={}",
                        productId, r.stock()));
    }

    /**
     * OJO: aquí NO se pone @Transactional a propósito. En un flujo WebFlux
     * con JPA bloqueante (Mono.fromCallable + subscribeOn(boundedElastic)),
     * @Transactional sobre este método no protegería nada: el proxy de
     * Spring abre/cierra la transacción de forma síncrona alrededor de la
     * llamada al método (que solo devuelve un Mono "frío" al instante),
     * mientras que el trabajo real ocurre después, en otro hilo — la
     * transacción ya estaría cerrada. La atomicidad real la da el único
     * repository.save() de más abajo (Spring Data JPA ya es transaccional
     * por sí mismo) combinado con el bloqueo optimista de {@link InventoryItem#getVersion()}.
     */
    /**
     * [PASO 14 · FLUJO "Crear pedido"] — El descuento real de stock, con
     * bloqueo optimista (@Version en InventoryItem, ver domain/InventoryItem.java).
     * La respuesta vuelve por InventoryController -> por la red ->
     * InventoryClient.reserveStock (order-service) -> se resuelve el
     * .flatMap del PASO 12 -> transitionAndSave(CONFIRMED), PASO 15.
     */
    @Override
    public Mono<AvailabilityResponse> reserveStock(ReserveStockRequest request) {
        return Mono.fromCallable(() -> {
                    InventoryItem item = repository.findByProductId(request.productId())
                            .orElseThrow(() -> new ProductNotFoundException(request.productId()));

                    if (item.getStock() < request.quantity()) {
                        throw new InsufficientStockException(request.productId(), request.quantity(), item.getStock());
                    }

                    item.setStock(item.getStock() - request.quantity());
                    return repository.save(item);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toAvailabilityResponse)
                .doOnSuccess(r -> log.info("Stock reservado para producto {}: nuevo stock={}",
                        request.productId(), r.stock()));
    }

    private AvailabilityResponse toAvailabilityResponse(InventoryItem item) {
        return new AvailabilityResponse(
                item.getProductId(),
                item.getProductName(),
                item.getStock(),
                item.getStock() > 0
        );
    }
}
