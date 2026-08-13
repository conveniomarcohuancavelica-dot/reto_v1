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
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository repository;

    @Override
    public Mono<AvailabilityResponse> checkAvailability(String productId) {
        return Mono.fromCallable(() -> repository.findByProductId(productId)
                        .orElseThrow(() -> new ProductNotFoundException(productId)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toAvailabilityResponse)
                .doOnSuccess(r -> log.info("Consulta de disponibilidad para producto {}: stock={}",
                        productId, r.stock()));
    }

    @Override
    @Transactional
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
