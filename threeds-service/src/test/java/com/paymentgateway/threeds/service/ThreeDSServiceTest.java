package com.paymentgateway.threeds.service;

import com.paymentgateway.threeds.domain.BrowserInfo;
import com.paymentgateway.threeds.domain.ThreeDSStatus;
import com.paymentgateway.threeds.domain.ThreeDSTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreeDSServiceTest {

    @Mock
    private RedisTemplate<String, ThreeDSTransaction> redisTemplate;

    @Mock
    private ValueOperations<String, ThreeDSTransaction> valueOperations;

    @Mock
    private ACSSimulator acsSimulator;

    private ThreeDSService threeDSService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        threeDSService = new ThreeDSService(redisTemplate, acsSimulator);
    }

    @Test
    void shouldProcessFrictionlessFlowForLowAmounts() {
        // Given
        String transactionId = "txn_123";
        String merchantId = "merchant_001";
        BigDecimal amount = new BigDecimal("50.00"); // Low amount
        String currency = "USD";
        String cardToken = "tok_abc123";
        String merchantReturnUrl = "https://merchant.com/return";
        BrowserInfo browserInfo = createBrowserInfo();

        // When
        ThreeDSTransaction result = threeDSService.initiateAuthentication(
            transactionId, merchantId, amount, currency, cardToken, 
            merchantReturnUrl, browserInfo
        );

        // Then
        assertThat(result.getStatus()).isEqualTo(ThreeDSStatus.FRICTIONLESS);
        assertThat(result.getCavv()).isNotNull().isNotEmpty();
        assertThat(result.getEci()).isEqualTo("05"); // Fully authenticated
        assertThat(result.getXid()).isNotNull().isNotEmpty();
        assertThat(result.getAcsUrl()).isNullOrEmpty();
        
        verify(valueOperations).set(anyString(), any(ThreeDSTransaction.class), any(Duration.class));
    }

    @Test
    void shouldRequireChallengeForHighAmounts() {
        // Given
        String transactionId = "txn_456";
        String merchantId = "merchant_001";
        BigDecimal amount = new BigDecimal("500.00"); // High amount
        String currency = "USD";
        String cardToken = "tok_abc123";
        String merchantReturnUrl = "https://merchant.com/return";
        BrowserInfo browserInfo = createBrowserInfo();

        when(acsSimulator.getAcsUrl()).thenReturn("https://acs.example.com/auth");

        // When
        ThreeDSTransaction result = threeDSService.initiateAuthentication(
            transactionId, merchantId, amount, currency, cardToken, 
            merchantReturnUrl, browserInfo
        );

        // Then
        assertThat(result.getStatus()).isEqualTo(ThreeDSStatus.CHALLENGE_REQUIRED);
        assertThat(result.getAcsUrl()).isEqualTo("https://acs.example.com/auth");
        assertThat(result.getXid()).isNotNull().isNotEmpty();
        assertThat(result.getCavv()).isNullOrEmpty(); // Not yet authenticated
        
        verify(valueOperations).set(anyString(), any(ThreeDSTransaction.class), any(Duration.class));
    }

    @Test
    void shouldCompleteAuthenticationSuccessfully() {
        // Given
        String transactionId = "txn_789";
        String pares = "valid-pares-response";
        
        ThreeDSTransaction existingTransaction = new ThreeDSTransaction(
            transactionId, "merchant_001", new BigDecimal("500.00"), 
            "USD", "tok_abc123", "https://merchant.com/return", createBrowserInfo()
        );
        existingTransaction.setStatus(ThreeDSStatus.CHALLENGE_REQUIRED);
        existingTransaction.setXid("xid_123");

        when(valueOperations.get(anyString())).thenReturn(existingTransaction);
        when(acsSimulator.validatePARes(pares)).thenReturn(true);

        // When
        ThreeDSTransaction result = threeDSService.completeAuthentication(transactionId, pares);

        // Then
        assertThat(result.getStatus()).isEqualTo(ThreeDSStatus.AUTHENTICATED);
        assertThat(result.getCavv()).isNotNull().isNotEmpty();
        assertThat(result.getEci()).isEqualTo("05");
        assertThat(result.getXid()).isEqualTo("xid_123");
        
        verify(acsSimulator).validatePARes(pares);
        verify(valueOperations, times(1)).set(anyString(), any(ThreeDSTransaction.class), any(Duration.class));
    }

    @Test
    void shouldFailAuthenticationWithInvalidPARes() {
        // Given
        String transactionId = "txn_fail";
        String pares = "invalid-pares";
        
        ThreeDSTransaction existingTransaction = new ThreeDSTransaction(
            transactionId, "merchant_001", new BigDecimal("500.00"), 
            "USD", "tok_abc123", "https://merchant.com/return", createBrowserInfo()
        );
        existingTransaction.setStatus(ThreeDSStatus.CHALLENGE_REQUIRED);

        when(valueOperations.get(anyString())).thenReturn(existingTransaction);
        when(acsSimulator.validatePARes(pares)).thenReturn(false);

        // When
        ThreeDSTransaction result = threeDSService.completeAuthentication(transactionId, pares);

        // Then
        assertThat(result.getStatus()).isEqualTo(ThreeDSStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("Authentication failed");
        assertThat(result.getCavv()).isNullOrEmpty();
        
        verify(acsSimulator).validatePARes(pares);
    }

    @Test
    void shouldHandleAuthenticationTimeout() {
        // Given
        String transactionId = "txn_timeout";
        String pares = "valid-pares";
        
        ThreeDSTransaction expiredTransaction = new ThreeDSTransaction(
            transactionId, "merchant_001", new BigDecimal("500.00"), 
            "USD", "tok_abc123", "https://merchant.com/return", createBrowserInfo()
        );
        expiredTransaction.setStatus(ThreeDSStatus.CHALLENGE_REQUIRED);
        expiredTransaction.setExpiresAt(Instant.now().minusSeconds(60)); // Expired 1 minute ago

        when(valueOperations.get(anyString())).thenReturn(expiredTransaction);

        // When
        ThreeDSTransaction result = threeDSService.completeAuthentication(transactionId, pares);

        // Then
        assertThat(result.getStatus()).isEqualTo(ThreeDSStatus.TIMEOUT);
        assertThat(result.getErrorMessage()).isEqualTo("Authentication timeout");
        
        verify(acsSimulator, never()).validatePARes(anyString());
    }

    @Test
    void shouldThrowExceptionForNonExistentTransaction() {
        // Given
        String transactionId = "txn_nonexistent";
        String pares = "valid-pares";

        when(valueOperations.get(anyString())).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> threeDSService.completeAuthentication(transactionId, pares))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Transaction not found");
    }

    @Test
    void shouldRetrieveTransactionFromRedis() {
        // Given
        String transactionId = "txn_retrieve";
        ThreeDSTransaction transaction = new ThreeDSTransaction(
            transactionId, "merchant_001", new BigDecimal("100.00"), 
            "USD", "tok_abc123", "https://merchant.com/return", createBrowserInfo()
        );

        when(valueOperations.get("3ds:transaction:" + transactionId)).thenReturn(transaction);

        // When
        ThreeDSTransaction result = threeDSService.getTransaction(transactionId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTransactionId()).isEqualTo(transactionId);
        
        verify(valueOperations).get("3ds:transaction:" + transactionId);
    }

    @Test
    void shouldGenerateUniqueXidForEachTransaction() {
        // Given
        BrowserInfo browserInfo = createBrowserInfo();

        // When
        ThreeDSTransaction tx1 = threeDSService.initiateAuthentication(
            "txn_1", "merchant_001", new BigDecimal("50.00"), 
            "USD", "tok_1", "https://merchant.com/return", browserInfo
        );
        
        ThreeDSTransaction tx2 = threeDSService.initiateAuthentication(
            "txn_2", "merchant_001", new BigDecimal("50.00"), 
            "USD", "tok_2", "https://merchant.com/return", browserInfo
        );

        // Then
        assertThat(tx1.getXid()).isNotEqualTo(tx2.getXid());
    }

    private BrowserInfo createBrowserInfo() {
        return new BrowserInfo(
            "Mozilla/5.0",
            "text/html",
            "en-US",
            1920,
            1080,
            24,
            "-300",
            false,
            true,
            "192.168.1.1"
        );
    }
}
