package com.reto.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce cualquier error capturado por el Gateway (401 token inválido/expirado,
 * 404 ruta no encontrada, 503 servicio caído, etc.) al formato de error único
 * exigido por el reto: { timestamp, status, code, message, traceId }.
 */
@Order(-2)
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    public GlobalErrorWebExceptionHandler(ErrorAttributes errorAttributes,
                                           WebProperties webProperties,
                                           ApplicationContext applicationContext,
                                           ServerCodecConfigurer codecConfigurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        this.setMessageWriters(codecConfigurer.getWriters());
        this.setMessageReaders(codecConfigurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(org.springframework.web.reactive.function.server.RequestPredicates.all(),
                this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Map<String, Object> errorPropertiesMap = getErrorAttributes(request,
                ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE));

        int status = (int) errorPropertiesMap.getOrDefault("status", 500);
        String traceId = request.exchange().getResponse().getHeaders().getFirst("X-Trace-Id");
        if (traceId == null) {
            traceId = request.headers().firstHeader("X-Trace-Id");
        }

        String code = mapStatusToCode(status);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("code", code);
        body.put("message", errorPropertiesMap.getOrDefault("message", "Error inesperado"));
        body.put("traceId", traceId);

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }

    private String mapStatusToCode(int status) {
        return switch (status) {
            case 401 -> "UNAUTHORIZED_TOKEN_INVALID";
            case 403 -> "FORBIDDEN";
            case 404 -> "ROUTE_NOT_FOUND";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
    }
}
