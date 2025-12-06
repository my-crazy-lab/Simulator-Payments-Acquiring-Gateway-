package com.paymentgateway.authorization.security;

import com.paymentgateway.authorization.domain.Merchant;
import com.paymentgateway.authorization.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationEdgeCasesTest {
    
    @Mock
    private MerchantRepository merchantRepository;
    
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    
    private PasswordEncoder passwordEncoder;
    private MerchantAuthenticationService authenticationService;
    
    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authenticationService = new MerchantAuthenticationService(
            merchantRepository,
            passwordEncoder,
            jwtTokenProvider
        );
    }
    
    // Test expired tokens
    
    @Test
    void shouldRejectExpiredJwtToken() {
        String expiredToken = "expired.jwt.token";
        
        when(jwtTokenProvider.validateToken(expiredToken)).thenReturn(false);
        
        Optional<Merchant> result = authenticationService.authenticateByJwt(expiredToken);
        
        assertThat(result).isEmpty();
        verify(jwtTokenProvider).validateToken(expiredToken);
    }
    
    @Test
    void shouldRejectExpiredTokenEvenIfMerchantExists() {
        String expiredToken = "expired.jwt.token";
        String merchantId = "MERCHANT_001";
        
        Merchant merchant = new Merchant(merchantId, "Test Merchant");
        merchant.setIsActive(true);
        
        when(jwtTokenProvider.validateToken(expiredToken)).thenReturn(false);
        
        Optional<Merchant> result = authenticationService.authenticateByJwt(expiredToken);
        
        assertThat(result).isEmpty();
        verify(merchantRepository, never()).findByMerchantId(any());
    }
    
    @Test
    void shouldRejectValidTokenForInactiveMerchant() {
        String validToken = "valid.jwt.token";
        String merchantId = "MERCHANT_001";
        
        Merchant merchant = new Merchant(merchantId, "Test Merchant");
        merchant.setIsActive(false); // Inactive
        
        when(jwtTokenProvider.validateToken(validToken)).thenReturn(true);
        when(jwtTokenProvider.getMerchantIdFromToken(validToken)).thenReturn(merchantId);
        when(merchantRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(merchant));
        
        Optional<Merchant> result = authenticationService.authenticateByJwt(validToken);
        
        assertThat(result).isEmpty();
    }
    
    // Test invalid API keys
    
    @Test
    void shouldRejectNullApiKey() {
        Optional<Merchant> result = authenticationService.authenticateByApiKey(null);
        
        assertThat(result).isEmpty();
        verify(merchantRepository, never()).findAll();
    }
    
    @Test
    void shouldRejectEmptyApiKey() {
        Optional<Merchant> result = authenticationService.authenticateByApiKey("");
        
        assertThat(result).isEmpty();
        verify(merchantRepository, never()).findAll();
    }
    
    @Test
    void shouldRejectBlankApiKey() {
        Optional<Merchant> result = authenticationService.authenticateByApiKey("   ");
        
        assertThat(result).isEmpty();
        verify(merchantRepository, never()).findAll();
    }
    
    @Test
    void shouldRejectInvalidApiKey() {
        String invalidApiKey = "sk_invalid_key";
        
        Merchant merchant = new Merchant("MERCHANT_001", "Test Merchant");
        merchant.setApiKeyHash(passwordEncoder.encode("sk_valid_key"));
        merchant.setIsActive(true);
        
        when(merchantRepository.findAll()).thenReturn(List.of(merchant));
        
        Optional<Merchant> result = authenticationService.authenticateByApiKey(invalidApiKey);
        
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldRejectApiKeyForInactiveMerchant() {
        String apiKey = "sk_valid_key";
        
        Merchant merchant = new Merchant("MERCHANT_001", "Test Merchant");
        merchant.setApiKeyHash(passwordEncoder.encode(apiKey));
        merchant.setIsActive(false); // Inactive
        
        when(merchantRepository.findAll()).thenReturn(List.of(merchant));
        
        Optional<Merchant> result = authenticationService.authenticateByApiKey(apiKey);
        
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldRejectApiKeyWhenMerchantHasNoApiKey() {
        String apiKey = "sk_some_key";
        
        Merchant merchant = new Merchant("MERCHANT_001", "Test Merchant");
        merchant.setApiKeyHash(null); // No API key set
        merchant.setIsActive(true);
        
        when(merchantRepository.findAll()).thenReturn(List.of(merchant));
        
        Optional<Merchant> result = authenticationService.authenticateByApiKey(apiKey);
        
        assertThat(result).isEmpty();
    }
    
    // Test missing credentials
    
    @Test
    void shouldRejectNullJwtToken() {
        Optional<Merchant> result = authenticationService.authenticateByJwt(null);
        
        assertThat(result).isEmpty();
        verify(jwtTokenProvider, never()).validateToken(any());
    }
    
    @Test
    void shouldRejectEmptyJwtToken() {
        Optional<Merchant> result = authenticationService.authenticateByJwt("");
        
        assertThat(result).isEmpty();
        verify(jwtTokenProvider, never()).validateToken(any());
    }
    
    @Test
    void shouldRejectBlankJwtToken() {
        Optional<Merchant> result = authenticationService.authenticateByJwt("   ");
        
        assertThat(result).isEmpty();
        verify(jwtTokenProvider, never()).validateToken(any());
    }
    
    @Test
    void shouldHandleMalformedJwtToken() {
        String malformedToken = "not.a.valid.jwt.token.format";
        
        when(jwtTokenProvider.validateToken(malformedToken)).thenReturn(false);
        
        Optional<Merchant> result = authenticationService.authenticateByJwt(malformedToken);
        
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldHandleJwtTokenWithInvalidSignature() {
        String tokenWithInvalidSignature = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.invalid_signature";
        
        when(jwtTokenProvider.validateToken(tokenWithInvalidSignature)).thenReturn(false);
        
        Optional<Merchant> result = authenticationService.authenticateByJwt(tokenWithInvalidSignature);
        
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldHandleJwtTokenForNonExistentMerchant() {
        String validToken = "valid.jwt.token";
        String merchantId = "NON_EXISTENT";
        
        when(jwtTokenProvider.validateToken(validToken)).thenReturn(true);
        when(jwtTokenProvider.getMerchantIdFromToken(validToken)).thenReturn(merchantId);
        when(merchantRepository.findByMerchantId(merchantId)).thenReturn(Optional.empty());
        
        Optional<Merchant> result = authenticationService.authenticateByJwt(validToken);
        
        assertThat(result).isEmpty();
    }
    
    // Test API key generation and revocation
    
    @Test
    void shouldGenerateValidApiKey() {
        Merchant merchant = new Merchant("MERCHANT_001", "Test Merchant");
        merchant.setIsActive(true);
        
        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant);
        
        String apiKey = authenticationService.createApiKey(merchant);
        
        assertThat(apiKey).isNotNull();
        assertThat(apiKey).startsWith("sk_");
        assertThat(apiKey.length()).isGreaterThan(10);
        verify(merchantRepository).save(merchant);
    }
    
    @Test
    void shouldRevokeApiKey() {
        Merchant merchant = new Merchant("MERCHANT_001", "Test Merchant");
        merchant.setApiKeyHash(passwordEncoder.encode("sk_old_key"));
        merchant.setIsActive(true);
        
        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant);
        
        authenticationService.revokeApiKey(merchant);
        
        assertThat(merchant.getApiKeyHash()).isNull();
        verify(merchantRepository).save(merchant);
    }
    
    // Test concurrent authentication attempts
    
    @Test
    void shouldHandleMultipleMerchantsWithDifferentApiKeys() {
        String apiKey1 = "sk_key_1";
        String apiKey2 = "sk_key_2";
        
        Merchant merchant1 = new Merchant("MERCHANT_001", "Merchant 1");
        merchant1.setApiKeyHash(passwordEncoder.encode(apiKey1));
        merchant1.setIsActive(true);
        
        Merchant merchant2 = new Merchant("MERCHANT_002", "Merchant 2");
        merchant2.setApiKeyHash(passwordEncoder.encode(apiKey2));
        merchant2.setIsActive(true);
        
        when(merchantRepository.findAll()).thenReturn(List.of(merchant1, merchant2));
        
        Optional<Merchant> result1 = authenticationService.authenticateByApiKey(apiKey1);
        assertThat(result1).isPresent();
        assertThat(result1.get().getMerchantId()).isEqualTo("MERCHANT_001");
        
        when(merchantRepository.findAll()).thenReturn(List.of(merchant1, merchant2));
        
        Optional<Merchant> result2 = authenticationService.authenticateByApiKey(apiKey2);
        assertThat(result2).isPresent();
        assertThat(result2.get().getMerchantId()).isEqualTo("MERCHANT_002");
    }
}
