package com.reto.inventory.filter;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Recibe el X-Trace-Id propagado por el API Gateway (o lo genera si el
 * servicio se invoca de forma aislada, ej. en pruebas) y lo agrega al
 * contexto reactivo y al MDC para que aparezca en los logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        final String finalTraceId = traceId;

        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        return chain.filter(exchange)
                .contextWrite(Context.of(MDC_TRACE_ID_KEY, finalTraceId))
                .doFirst(() -> MDC.put(MDC_TRACE_ID_KEY, finalTraceId))
                .doFinally(signalType -> MDC.remove(MDC_TRACE_ID_KEY));
    }
}
