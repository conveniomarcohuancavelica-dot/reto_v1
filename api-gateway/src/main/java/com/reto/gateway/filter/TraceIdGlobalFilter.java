package com.reto.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filtro global del API Gateway responsable de la trazabilidad end-to-end.
 *
 * Responsabilidades:
 *  - Si la petición entrante ya trae X-Trace-Id, se respeta (permite que un
 *    cliente externo correlacione su propia traza).
 *  - Si no lo trae, se genera uno nuevo (UUID) en este punto de entrada.
 *  - Se propaga el header hacia los microservicios downstream.
 *  - Se agrega también a la respuesta, para que el cliente (Postman) lo vea.
 *  - Se coloca en el MDC de logging para que aparezca en cada línea de log
 *    de este componente.
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest originalRequest = exchange.getRequest();

        String traceId = originalRequest.getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        final String finalTraceId = traceId;

        ServerHttpRequest mutatedRequest = originalRequest.mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return chain.filter(mutatedExchange)
                .doOnEach(signal -> MDC.put(MDC_TRACE_ID_KEY, finalTraceId))
                .contextWrite(reactor.util.context.Context.of(MDC_TRACE_ID_KEY, finalTraceId));
    }

    @Override
    public int getOrder() {
        // Debe ejecutarse antes que cualquier otro filtro (incluida la seguridad),
        // para que el traceId esté disponible incluso en respuestas de error 401/403.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
