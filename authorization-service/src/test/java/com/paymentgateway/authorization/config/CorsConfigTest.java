package com.paymentgateway.authorization.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CorsConfig.
 * Tests CORS policy enforcement including origin validation,
 * allowed methods, and headers.
 * 
 * Requirements: 24.3
 */
class CorsConfigTest {
    
    private CorsConfig corsConfig;
    
    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig();
    }
    
    @Nested
    @DisplayName("Origin Validation Tests")
    class OriginValidationTests {
        
        @Test
        @DisplayName("Should allow configured origins")
        void shouldAllowConfiguredOrigins() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedOrigins", 
                "https://merchant1.com,https://merchant2.com");
            
            // Act & Assert
            assertThat(corsConfig.isOriginAllowed("https://merchant1.com")).isTrue();
            assertThat(corsConfig.isOriginAllowed("https://merchant2.com")).isTrue();
        }
        
        @Test
        @DisplayName("Should reject non-configured origins")
        void shouldRejectNonConfiguredOrigins() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedOrigins", 
                "https://merchant1.com");
            
            // Act & Assert
            assertThat(corsConfig.isOriginAllowed("https://malicious.com")).isFalse();
            assertThat(corsConfig.isOriginAllowed("https://attacker.com")).isFalse();
        }
        
        @Test
        @DisplayName("Should reject null origin")
        void shouldRejectNullOrigin() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedOrigins", 
                "https://merchant1.com");
            
            // Act & Assert
            assertThat(corsConfig.isOriginAllowed(null)).isFalse();
        }
        
        @Test
        @DisplayName("Should reject empty origin")
        void shouldRejectEmptyOrigin() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedOrigins", 
                "https://merchant1.com");
            
            // Act & Assert
            assertThat(corsConfig.isOriginAllowed("")).isFalse();
        }
        
        @Test
        @DisplayName("Should reject all origins when none configured")
        void shouldRejectAllOriginsWhenNoneConfigured() {
            // Arrange - no origins configured (default)
            ReflectionTestUtils.setField(corsConfig, "allowedOrigins", "");
            
            // Act & Assert
            assertThat(corsConfig.isOriginAllowed("https://any-origin.com")).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Allowed Methods Tests")
    class AllowedMethodsTests {
        
        @Test
        @DisplayName("Should return configured allowed methods")
        void shouldReturnConfiguredAllowedMethods() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedMethods", 
                "GET,POST,PUT,DELETE,OPTIONS");
            
            // Act
            List<String> methods = corsConfig.getAllowedMethods();
            
            // Assert
            assertThat(methods).containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        }
        
        @Test
        @DisplayName("Should return empty list when no methods configured")
        void shouldReturnEmptyListWhenNoMethodsConfigured() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedMethods", "");
            
            // Act
            List<String> methods = corsConfig.getAllowedMethods();
            
            // Assert
            assertThat(methods).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("Allowed Headers Tests")
    class AllowedHeadersTests {
        
        @Test
        @DisplayName("Should return configured allowed headers")
        void shouldReturnConfiguredAllowedHeaders() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedHeaders", 
                "Authorization,Content-Type,X-Idempotency-Key,X-Request-ID");
            
            // Act
            List<String> headers = corsConfig.getAllowedHeaders();
            
            // Assert
            assertThat(headers).containsExactly(
                "Authorization", "Content-Type", "X-Idempotency-Key", "X-Request-ID");
        }
        
        @Test
        @DisplayName("Should include Authorization header")
        void shouldIncludeAuthorizationHeader() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedHeaders", 
                "Authorization,Content-Type");
            
            // Act
            List<String> headers = corsConfig.getAllowedHeaders();
            
            // Assert
            assertThat(headers).contains("Authorization");
        }
    }
    
    @Nested
    @DisplayName("CORS Configuration Source Tests")
    class CorsConfigurationSourceTests {
        
        @Test
        @DisplayName("Should create CORS configuration source")
        void shouldCreateCorsConfigurationSource() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedOrigins", 
                "https://merchant.com");
            ReflectionTestUtils.setField(corsConfig, "allowedMethods", 
                "GET,POST");
            ReflectionTestUtils.setField(corsConfig, "allowedHeaders", 
                "Authorization,Content-Type");
            ReflectionTestUtils.setField(corsConfig, "exposedHeaders", 
                "X-Request-ID");
            ReflectionTestUtils.setField(corsConfig, "maxAge", 3600L);
            ReflectionTestUtils.setField(corsConfig, "allowCredentials", false);
            
            // Act
            CorsConfigurationSource source = corsConfig.corsConfigurationSource();
            
            // Assert
            assertThat(source).isNotNull();
            assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        }
        
        @Test
        @DisplayName("Should apply configuration to /api/** paths")
        void shouldApplyConfigurationToApiPaths() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedOrigins", 
                "https://merchant.com");
            ReflectionTestUtils.setField(corsConfig, "allowedMethods", 
                "GET,POST");
            ReflectionTestUtils.setField(corsConfig, "allowedHeaders", 
                "Authorization");
            ReflectionTestUtils.setField(corsConfig, "exposedHeaders", 
                "X-Request-ID");
            ReflectionTestUtils.setField(corsConfig, "maxAge", 3600L);
            ReflectionTestUtils.setField(corsConfig, "allowCredentials", false);
            
            // Act
            CorsConfigurationSource source = corsConfig.corsConfigurationSource();
            
            // Assert - The source should be configured for /api/** paths
            assertThat(source).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Security Policy Tests")
    class SecurityPolicyTests {
        
        @ParameterizedTest
        @ValueSource(strings = {
            "http://localhost:3000",
            "http://127.0.0.1:8080",
            "file:///path/to/file"
        })
        @DisplayName("Should reject potentially dangerous origins when not configured")
        void shouldRejectDangerousOrigins(String dangerousOrigin) {
            // Arrange - only allow specific HTTPS origins
            ReflectionTestUtils.setField(corsConfig, "allowedOrigins", 
                "https://secure-merchant.com");
            
            // Act & Assert
            assertThat(corsConfig.isOriginAllowed(dangerousOrigin)).isFalse();
        }
        
        @Test
        @DisplayName("Should not allow wildcard origin by default")
        void shouldNotAllowWildcardOriginByDefault() {
            // Arrange
            ReflectionTestUtils.setField(corsConfig, "allowedOrigins", "");
            
            // Act
            List<String> origins = corsConfig.getAllowedOrigins();
            
            // Assert - should not contain wildcard
            assertThat(origins).doesNotContain("*");
        }
    }
}
