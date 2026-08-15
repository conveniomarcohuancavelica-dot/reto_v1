package com.reto.order.client;

import com.reto.order.dto.InventoryAvailabilityResponse;
import com.reto.order.exception.InventoryServiceUnavailableException;
import com.reto.order.exception.StockInsufficientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Encapsula toda la comunicación HTTP reactiva (WebClient) hacia
 * Inventory Service. Es el único punto del código que sabe cómo llamar a
 * ese microservicio (principio de responsabilidad única / bajo acoplamiento).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryClient {

    private final WebClient inventoryWebClient;

    /**
     * [PASO 7 · FLUJO "Crear pedido"] — Se llama desde OrderServiceImpl
     * (paso 4/5), justo después de guardar el pedido en PENDING. Esta es la
     * llamada HTTP reactiva que SALE de order-service hacia inventory-service
     * (el traceId viaja automático porque WebClient está configurado para
     * propagar headers — ver WebClientConfig.java). El siguiente archivo en
     * la secuencia, del lado de inventory-service, es su TraceIdWebFilter.
     */
    public Mono<InventoryAvailabilityResponse> checkAvailability(String productId) {
        return inventoryWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/inventory/availability")
                        .queryParam("productId", productId)
                        .build())
                .retrieve()
                .bodyToMono(InventoryAvailabilityResponse.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(this::mapDownstreamError)
                .doOnSubscribe(sub -> log.debug("Consultando disponibilidad de producto {} en Inventory Service", productId));
    }

    /**
     * [PASO 12 · FLUJO "Crear pedido"] — Segunda llamada saliente hacia
     * inventory-service, ahora para descontar el stock. Solo se llega aquí
     * si el PASO 11 (confirmOrIndicateStock) confirmó que hay stock
     * suficiente. Del lado de inventory-service, esta vez el destino es
     * InventoryController.reserveStock() -> PASO 13.
     */
    public Mono<InventoryAvailabilityResponse> reserveStock(String productId, int quantity) {
        return inventoryWebClient.post()
                .uri("/api/v1/inventory/reserve")
                .bodyValue(Map.of("productId", productId, "quantity", quantity))
                .retrieve()
                .bodyToMono(InventoryAvailabilityResponse.class)
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(this::mapDownstreamError);
    }

    private Throwable mapDownstreamError(Throwable error) {
        if (error instanceof WebClientResponseException wcre) {
            HttpStatusCode status = wcre.getStatusCode();
            if (status.value() == 409) {
                return new StockInsufficientException("producto consultado");
            }
            return new InventoryServiceUnavailableException(
                    "Inventory Service respondió con error: " + status.value(), wcre);
        }
        return new InventoryServiceUnavailableException(
                "Inventory Service no disponible o no respondió a tiempo", error);
    }
}
