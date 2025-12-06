package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.domain.Merchant;
import com.paymentgateway.authorization.repository.MerchantRepository;
import com.paymentgateway.authorization.security.JwtTokenProvider;
import com.paymentgateway.authorization.security.MerchantAuthenticationService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Feature: payment-acquiring-gateway, Property 24: Authentication Required
 * 
 * For any API request without valid authentication (API key or JWT token), 
 * the request should be rejected with HTTP 401 status.
 * 
 * Validates: Requirements 13.1
 */
public class AuthenticationRequiredPropertyTest {
    
    /**
     * Property: For any invalid or missing API key, authentication should fail
     */
    @Property(tries = 100)
    void invalidApiKeyShouldFailAuthentication(
            @ForAll @AlphaChars @StringLength(min = 10, max = 50) String invalidApiKey) {
        
        // Arrange
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        MerchantAuthenticationService authenticationService = new MerchantAuthenticationService(
            merchantRepository,
            passwordEncoder,
            jwtTokenProvider
        );
        
        // Mock: no merchants in database
        when(merchantRepository.findAll()).thenReturn(List.of());
        
        // Act - Try to authenticate with invalid API key
        Optional<Merchant> result = authenticationService.authenticateByApiKey(invalidApiKey);
        
        // Assert - Authentication should fail (empty Optional)
        assertThat(result)
            .as("Authentication with invalid API key should fail")
            .isEmpty();
    }
    
    /**
     * Property: For any null or blank API key, authentication should fail
     */
    @Property(tries = 100)
    void nullOrBlankApiKeyShouldFailAuthentication(
            @ForAll("nullOrBlankStrings") String invalidApiKey) {
        
        // Arrange
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        MerchantAuthenticationService authenticationService = new MerchantAuthenticationService(
            merchantRepository,
            passwordEncoder,
            jwtTokenProvider
        );
        
        // Act
        Optional<Merchant> result = authenticationService.authenticateByApiKey(invalidApiKey);
        
        // Assert
        assertThat(result)
            .as("Authentication with null or blank API key should fail")
            .isEmpty();
    }
    
    /**
     * Property: For any invalid JWT token, authentication should fail
     */
    @Property(tries = 100)
    void invalidJwtTokenShouldFailAuthentication(
            @ForAll @AlphaChars @StringLength(min = 20, max = 100) String invalidToken) {
        
        // Arrange
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        MerchantAuthenticationService authenticationService = new MerchantAuthenticationService(
            merchantRepository,
            passwordEncoder,
            jwtTokenProvider
        );
        
        // Mock: token validation fails
        when(jwtTokenProvider.validateToken(invalidToken)).thenReturn(false);
        
        // Act - Try to authenticate with invalid JWT token
        Optional<Merchant> result = authenticationService.authenticateByJwt(invalidToken);
        
        // Assert - Authentication should fail
        assertThat(result)
            .as("Authentication with invalid JWT token should fail")
            .isEmpty();
    }
    
    /**
     * Property: For any null or blank JWT token, authentication should fail
     */
    @Property(tries = 100)
    void nullOrBlankJwtTokenShouldFailAuthentication(
            @ForAll("nullOrBlankStrings") String invalidToken) {
        
        // Arrange
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        MerchantAuthenticationService authenticationService = new MerchantAuthenticationService(
            merchantRepository,
            passwordEncoder,
            jwtTokenProvider
        );
        
        // Act
        Optional<Merchant> result = authenticationService.authenticateByJwt(invalidToken);
        
        // Assert
        assertThat(result)
            .as("Authentication with null or blank JWT token should fail")
            .isEmpty();
    }
    
    /**
     * Property: For any inactive merchant, even with valid credentials, authentication should fail
     */
    @Property(tries = 100)
    void inactiveMerchantShouldFailAuthentication(
            @ForAll @AlphaChars @StringLength(min = 10, max = 30) String merchantId) {
        
        // Arrange
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        MerchantAuthenticationService authenticationService = new MerchantAuthenticationService(
            merchantRepository,
            passwordEncoder,
            jwtTokenProvider
        );
        
        // Create an inactive merchant
        Merchant merchant = new Merchant("INACTIVE_" + merchantId, "Inactive Merchant");
        merchant.setIsActive(false); // Inactive
        
        String token = "valid.jwt.token";
        
        // Mock: token is valid but merchant is inactive
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getMerchantIdFromToken(token)).thenReturn(merchant.getMerchantId());
        when(merchantRepository.findByMerchantId(merchant.getMerchantId())).thenReturn(Optional.of(merchant));
        
        // Act - Try to authenticate with valid token but inactive merchant
        Optional<Merchant> result = authenticationService.authenticateByJwt(token);
        
        // Assert - Authentication should fail because merchant is inactive
        assertThat(result)
            .as("Authentication should fail for inactive merchant even with valid token")
            .isEmpty();
    }
    
    @Provide
    Arbitrary<String> nullOrBlankStrings() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }
}
