package com.paymentgateway.authorization.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SecurityHeadersFilter.
 * Tests security headers including Content-Security-Policy, X-Frame-Options,
 * X-Content-Type-Options, and other security headers.
 * 
 * Requirements: 24.1
 */
@ExtendWith(MockitoExtension.class)
class SecurityHeadersFilterTest {
    
    private SecurityHeadersFilter filter;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;
    
    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
    }
    
    @Test
    @DisplayName("Should add Content-Security-Policy header")
    void shouldAddContentSecurityPolicyHeader() throws Exception {
        // Act
        filter.doFilterInternal(request, response, filterChain);
        
        // Assert
        verify(response).setHeader(eq("Content-Security-Policy"), anyString());
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    @DisplayName("Should add X-Frame-Options header with DENY value")
    void shouldAddXFrameOptionsHeader() throws Exception {
        // Act
        filter.doFilterInternal(request, response, filterChain);
        
        // Assert
        verify(response).setHeader("X-Frame-Options", "DENY");
    }
    
    @Test
    @DisplayName("Should add X-Content-Type-Options header with nosniff value")
    void shouldAddXContentTypeOptionsHeader() throws Exception {
        // Act
        filter.doFilterInternal(request, response, filterChain);
        
        // Assert
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
    }
    
    @Test
    @DisplayName("Should add X-XSS-Protection header")
    void shouldAddXXssProtectionHeader() throws Exception {
        // Act
        filter.doFilterInternal(request, response, filterChain);
        
        // Assert
        verify(response).setHeader("X-XSS-Protection", "1; mode=block");
    }
    
    @Test
    @DisplayName("Should add Strict-Transport-Security header")
    void shouldAddStrictTransportSecurityHeader() throws Exception {
        // Act
        filter.doFilterInternal(request, response, filterChain);
        
        // Assert
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("Strict-Transport-Security"), valueCaptor.capture());
        
        String hstsValue = valueCaptor.getValue();
        assertThat(hstsValue).contains("max-age=");
        assertThat(hstsValue).contains("includeSubDomains");
    }
    
    @Test
    @DisplayName("Should add Referrer-Policy header")
    void shouldAddReferrerPolicyHeader() throws Exception {
        // Act
        filter.doFilterInternal(request, response, filterChain);
        
        // Assert
        verify(response).setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    }
    
    @Test
    @DisplayName("Should add Permissions-Policy header")
    void shouldAddPermissionsPolicyHeader() throws Exception {
        // Act
        filter.doFilterInternal(request, response, filterChain);
        
        // Assert
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("Permissions-Policy"), valueCaptor.capture());
        
        String permissionsPolicy = valueCaptor.getValue();
        assertThat(permissionsPolicy).contains("geolocation=()");
        assertThat(permissionsPolicy).contains("camera=()");
    }
    
    @Test
    @DisplayName("Should add Cache-Control headers for sensitive data")
    void shouldAddCacheControlHeaders() throws Exception {
        // Act
        filter.doFilterInternal(request, response, filterChain);
        
        // Assert
        verify(response).setHeader(eq("Cache-Control"), contains("no-store"));
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setHeader("Expires", "0");
    }
    
    @Test
    @DisplayName("Should continue filter chain after adding headers")
    void shouldContinueFilterChain() throws Exception {
        // Act
        filter.doFilterInternal(request, response, filterChain);
        
        // Assert
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    @DisplayName("Content-Security-Policy should prevent frame-ancestors")
    void contentSecurityPolicyShouldPreventFrameAncestors() {
        // Act
        String csp = filter.getContentSecurityPolicy();
        
        // Assert
        assertThat(csp).contains("frame-ancestors 'none'");
    }
    
    @Test
    @DisplayName("Content-Security-Policy should restrict default-src to self")
    void contentSecurityPolicyShouldRestrictDefaultSrc() {
        // Act
        String csp = filter.getContentSecurityPolicy();
        
        // Assert
        assertThat(csp).contains("default-src 'self'");
    }
    
    @Test
    @DisplayName("Strict-Transport-Security should include preload")
    void strictTransportSecurityShouldIncludePreload() {
        // Act
        String hsts = filter.getStrictTransportSecurity();
        
        // Assert
        assertThat(hsts).contains("preload");
    }
    
    // Helper method for contains matcher
    private static String contains(String substring) {
        return argThat(arg -> arg != null && arg.contains(substring));
    }
}
