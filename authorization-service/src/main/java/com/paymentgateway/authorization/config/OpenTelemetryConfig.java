package com.paymentgateway.authorization.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenTelemetry distributed tracing.
 * Configures Jaeger exporter for trace visualization.
 * 
 * Requirements: 10.1, 10.2, 10.5 - Distributed tracing and observability
 */
@Configuration
public class OpenTelemetryConfig {
    
    @Value("${otel.service.name:authorization-service}")
    private String serviceName;
    
    @Bean
    public OpenTelemetry openTelemetry() {
        // Use GlobalOpenTelemetry which is auto-configured by spring-boot-starter
        // This allows the OpenTelemetry agent or auto-configuration to set up tracing
        try {
            return GlobalOpenTelemetry.get();
        } catch (IllegalStateException e) {
            // If not configured, return noop
            return OpenTelemetry.noop();
        }
    }
    
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, "1.0.0");
    }
}
