package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.AuthorizationServiceApplication;
import com.paymentgateway.authorization.domain.Merchant;
import com.paymentgateway.authorization.repository.MerchantRepository;
import com.paymentgateway.authorization.security.JwtTokenProvider;
import net.jqwik.api.*;
import net.jqwik.spring.JqwikSpringSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Feature: payment-acquiring-gateway, Property 25: Authorization Enforced
 * For any authenticated request, if the merchant lacks permission for the requested operation,
 * the request should be rejected with HTTP 403 status.
 * Validates: Requirements 13.2
 */
@JqwikSpringSupport
@SpringBootTest(
    classes = AuthorizationServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
public class AuthorizationEnforcedPropertyTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private MerchantRepository merchantRepository;
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    private String baseUrl;
    
    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        merchantRepository.deleteAll();
    }
    
    /**
     * Property: Merchants without required role should get 403 for protected endpoints
     */
    @Property(tries = 100)
    void merchantsWithoutRequiredRoleShouldGet403(
            @ForAll("merchantRoles") Set<String> roles,
            @ForAll("protectedEndpoints") ProtectedEndpoint endpoint) {
        
        // Create merchant with specific roles
        Merchant merchant = new Merchant("TEST_MERCHANT_" + System.nanoTime(), "Test Merchant");
        merchant.setRoles(roles);
        merchant.setIsActive(true);
        merchant = merchantRepository.save(merchant);
        
        // Generate valid JWT token
        String token = jwtTokenProvider.generateToken(merchant);
        
        // Check if merchant has required role
        boolean hasRequiredRole = roles.contains(endpoint.requiredRole);
        
        // Make request with authentication
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        
        HttpEntity<String> request = new HttpEntity<>("{}", headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + endpoint.path,
            endpoint.method,
            request,
            String.class
        );
        
        // If merchant doesn't have required role, should get 403
        if (!hasRequiredRole) {
            assert response.getStatusCode() == HttpStatus.FORBIDDEN :
                "Expected 403 for merchant without " + endpoint.requiredRole + 
                " role accessing " + endpoint.path + " but got " + response.getStatusCode();
        }
    }
    
    /**
     * Property: Inactive merchants should be denied access even with valid token
     */
    @Property(tries = 50)
    void inactiveMerchantsShouldBeDenied(@ForAll("merchantRoles") Set<String> roles) {
        // Create inactive merchant
        Merchant merchant = new Merchant("INACTIVE_" + System.nanoTime(), "Inactive Merchant");
        merchant.setRoles(roles);
        merchant.setIsActive(false);
        merchant = merchantRepository.save(merchant);
        
        // Generate token (before deactivation in real scenario)
        String token = jwtTokenProvider.generateToken(merchant);
        
        // Make request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        
        Map<String, Object> paymentRequest = createValidPaymentRequest();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(paymentRequest, headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/api/v1/payments",
            HttpMethod.POST,
            request,
            String.class
        );
        
        // Should be unauthorized since merchant is inactive
        assert response.getStatusCode() == HttpStatus.UNAUTHORIZED :
            "Expected 401 for inactive merchant but got " + response.getStatusCode();
    }
    
    // Providers
    
    @Provide
    Arbitrary<Set<String>> merchantRoles() {
        return Arbitraries.subsetOf(
            "PAYMENT_CREATE",
            "PAYMENT_READ",
            "PAYMENT_CAPTURE",
            "PAYMENT_VOID",
            "ADMIN"
        );
    }
    
    @Provide
    Arbitrary<ProtectedEndpoint> protectedEndpoints() {
        return Arbitraries.of(
            new ProtectedEndpoint("/api/v1/auth/api-keys", HttpMethod.POST, "ADMIN"),
            new ProtectedEndpoint("/api/v1/auth/api-keys", HttpMethod.DELETE, "ADMIN")
        );
    }
    
    private Map<String, Object> createValidPaymentRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("cardNumber", "4532015112830366");
        request.put("expiryMonth", 12);
        request.put("expiryYear", 2025);
        request.put("cvv", "123");
        request.put("amount", 100.00);
        request.put("currency", "USD");
        return request;
    }
    
    static class ProtectedEndpoint {
        final String path;
        final HttpMethod method;
        final String requiredRole;
        
        ProtectedEndpoint(String path, HttpMethod method, String requiredRole) {
            this.path = path;
            this.method = method;
            this.requiredRole = requiredRole;
        }
    }
}
