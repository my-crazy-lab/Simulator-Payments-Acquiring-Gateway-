package com.paymentgateway.authorization.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that adds security headers to all HTTP responses.
 * Implements security requirements for Content-Security-Policy, X-Frame-Options,
 * X-Content-Type-Options, and other security headers.
 * 
 * Requirements: 24.1
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {
    
    // Content Security Policy - restricts resource loading
    private static final String CONTENT_SECURITY_POLICY = 
        "default-src 'self'; " +
        "script-src 'self'; " +
        "style-src 'self'; " +
        "img-src 'self' data:; " +
        "font-src 'self'; " +
        "frame-ancestors 'none'; " +
        "form-action 'self'; " +
        "base-uri 'self'";
    
    // Strict Transport Security - enforce HTTPS
    private static final String STRICT_TRANSPORT_SECURITY = 
        "max-age=31536000; includeSubDomains; preload";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        
        // Add security headers
        addSecurityHeaders(response);
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Adds all required security headers to the response.
     * 
     * @param response The HTTP response to add headers to
     */
    public void addSecurityHeaders(HttpServletResponse response) {
        // Content-Security-Policy - prevents XSS and data injection attacks
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        
        // X-Frame-Options - prevents clickjacking
        response.setHeader("X-Frame-Options", "DENY");
        
        // X-Content-Type-Options - prevents MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // X-XSS-Protection - enables browser XSS filter (legacy, but still useful)
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // Strict-Transport-Security - enforces HTTPS
        response.setHeader("Strict-Transport-Security", STRICT_TRANSPORT_SECURITY);
        
        // Referrer-Policy - controls referrer information
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Permissions-Policy - restricts browser features
        response.setHeader("Permissions-Policy", 
            "geolocation=(), microphone=(), camera=(), payment=()");
        
        // Cache-Control - prevents caching of sensitive data
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
    
    /**
     * Returns the Content-Security-Policy header value.
     */
    public String getContentSecurityPolicy() {
        return CONTENT_SECURITY_POLICY;
    }
    
    /**
     * Returns the Strict-Transport-Security header value.
     */
    public String getStrictTransportSecurity() {
        return STRICT_TRANSPORT_SECURITY;
    }
}
