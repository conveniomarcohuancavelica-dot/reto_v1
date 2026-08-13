package com.reto.gateway.config;

import com.reto.gateway.exception.GlobalErrorWebExceptionHandler;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.server.WebExceptionHandler;

@Configuration
public class ErrorHandlingConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebExceptionHandler globalErrorWebExceptionHandler(ErrorAttributes errorAttributes,
                                                                WebProperties webProperties,
                                                                ApplicationContext applicationContext,
                                                                ServerCodecConfigurer codecConfigurer) {
        return new GlobalErrorWebExceptionHandler(errorAttributes, webProperties, applicationContext, codecConfigurer);
    }
}
