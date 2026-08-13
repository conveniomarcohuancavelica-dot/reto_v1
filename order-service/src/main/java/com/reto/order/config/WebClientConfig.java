package com.reto.order.config;

import com.reto.order.filter.TraceIdWebFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient inventoryWebClient(@Value("${inventory.service.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter(traceIdPropagationFilter())
                .build();
    }

    /**
     * Lee el X-Trace-Id del contexto reactivo (colocado ahí por TraceIdWebFilter
     * al recibir la petición original) y lo agrega como header saliente hacia
     * Inventory Service, cerrando la cadena de trazabilidad end-to-end.
     */
    private ExchangeFilterFunction traceIdPropagationFilter() {
        return (request, next) -> Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(TraceIdWebFilter.MDC_TRACE_ID_KEY, null);
            ClientRequest outgoing = traceId != null
                    ? ClientRequest.from(request).header(TraceIdWebFilter.TRACE_ID_HEADER, traceId).build()
                    : request;
            return next.exchange(outgoing);
        });
    }
}
