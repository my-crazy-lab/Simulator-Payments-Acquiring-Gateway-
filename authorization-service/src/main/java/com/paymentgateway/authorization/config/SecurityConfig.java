package com.paymentgateway.authorization.config;

import com.paymentgateway.authorization.security.AuthenticationFilter;
import com.paymentgateway.authorization.security.RateLimitFilter;
import com.paymentgateway.authorization.security.SecurityHeadersFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Security configuration for the Authorization Service.
 * Implements TLS 1.3 enforcement, security headers, and CORS policies.
 * 
 * Requirements: 1.5, 24.1, 24.2, 24.3, 24.4
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    private final AuthenticationFilter authenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final SecurityHeadersFilter securityHeadersFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    
    public SecurityConfig(AuthenticationFilter authenticationFilter, 
                         RateLimitFilter rateLimitFilter,
                         SecurityHeadersFilter securityHeadersFilter,
                         @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) {
        this.authenticationFilter = authenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.securityHeadersFilter = securityHeadersFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            
            // Stateless session management
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure security headers
            .headers(headers -> headers
                // X-Frame-Options: DENY
                .frameOptions(frame -> frame.deny())
                // X-Content-Type-Options: nosniff
                .contentTypeOptions(content -> {})
                // X-XSS-Protection
                .xssProtection(xss -> xss.headerValue(
                    org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                // Strict-Transport-Security
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                    .preload(true))
                // Referrer-Policy
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // Content-Security-Policy
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; " +
                        "img-src 'self' data:; font-src 'self'; frame-ancestors 'none'; " +
                        "form-action 'self'; base-uri 'self'"))
                // Permissions-Policy
                .permissionsPolicy(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=(), payment=()"))
            )
            
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                // Auth endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            
            // Add custom filters
            .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, AuthenticationFilter.class);
        
        return http.build();
    }
}
