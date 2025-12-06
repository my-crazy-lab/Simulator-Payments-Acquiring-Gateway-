package com.paymentgateway.authorization.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration.
 * Implements strict origin validation based on merchant configuration.
 * 
 * Requirements: 24.3
 */
@Configuration
public class CorsConfig {
    
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;
    
    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;
    
    @Value("${cors.allowed-headers:Authorization,Content-Type,X-Idempotency-Key,X-Request-ID}")
    private String allowedHeaders;
    
    @Value("${cors.exposed-headers:X-Request-ID,X-Trace-ID}")
    private String exposedHeaders;
    
    @Value("${cors.max-age:3600}")
    private long maxAge;
    
    @Value("${cors.allow-credentials:false}")
    private boolean allowCredentials;
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Set allowed origins - strict validation
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            configuration.setAllowedOrigins(parseList(allowedOrigins));
        } else {
            // Default: no origins allowed (most restrictive)
            configuration.setAllowedOrigins(List.of());
        }
        
        // Set allowed methods
        configuration.setAllowedMethods(parseList(allowedMethods));
        
        // Set allowed headers
        configuration.setAllowedHeaders(parseList(allowedHeaders));
        
        // Set exposed headers
        configuration.setExposedHeaders(parseList(exposedHeaders));
        
        // Set max age for preflight cache
        configuration.setMaxAge(maxAge);
        
        // Set credentials support
        configuration.setAllowCredentials(allowCredentials);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }
    
    private List<String> parseList(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(value.split(","));
    }
    
    /**
     * Validates if an origin is allowed based on configuration.
     * 
     * @param origin The origin to validate
     * @return true if the origin is allowed, false otherwise
     */
    public boolean isOriginAllowed(String origin) {
        if (origin == null || origin.isEmpty()) {
            return false;
        }
        
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return false;
        }
        
        List<String> origins = parseList(allowedOrigins);
        return origins.contains(origin);
    }
    
    /**
     * Returns the list of allowed origins.
     */
    public List<String> getAllowedOrigins() {
        return parseList(allowedOrigins);
    }
    
    /**
     * Returns the list of allowed methods.
     */
    public List<String> getAllowedMethods() {
        return parseList(allowedMethods);
    }
    
    /**
     * Returns the list of allowed headers.
     */
    public List<String> getAllowedHeaders() {
        return parseList(allowedHeaders);
    }
}
