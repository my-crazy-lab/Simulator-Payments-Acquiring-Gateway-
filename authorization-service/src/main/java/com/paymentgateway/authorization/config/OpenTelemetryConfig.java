package com.paymentgateway.authorization.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfig {
    
    @Value("${otel.service.name:authorization-service}")
    private String serviceName;
    
    // Temporarily disabled - requires additional OpenTelemetry dependencies
    // @Bean
    // public OpenTelemetry openTelemetry() {
    //     return OpenTelemetry.noop();
    // }
    
    // @Bean
    // public Tracer tracer(OpenTelemetry openTelemetry) {
    //     return openTelemetry.getTracer(serviceName);
    // }
}
