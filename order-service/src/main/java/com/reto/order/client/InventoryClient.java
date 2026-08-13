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
