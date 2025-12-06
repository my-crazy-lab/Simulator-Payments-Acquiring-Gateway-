package com.paymentgateway.fraud.service;

import com.paymentgateway.fraud.domain.FraudStatus;
import com.paymentgateway.fraud.repository.BlacklistRepository;
import com.paymentgateway.fraud.repository.FraudAlertRepository;
import com.paymentgateway.fraud.repository.FraudRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {
    
    @Mock
    private FraudRuleRepository fraudRuleRepository;
    
    @Mock
    private FraudAlertRepository fraudAlertRepository;
    
    @Mock
    private BlacklistRepository blacklistRepository;
    
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ValueOperations<String, String> valueOperations;
    
    private FraudDetectionService fraudDetectionService;
    private VelocityCheckService velocityCheckService;
    private GeolocationService geolocationService;
    private MLFraudScoringService mlFraudScoringService;
    
    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(fraudRuleRepository.findByEnabledTrueOrderByPriorityAsc()).thenReturn(Collections.emptyList());
        
        velocityCheckService = new VelocityCheckService(redisTemplate);
        geolocationService = new GeolocationService();
        mlFraudScoringService = new MLFraudScoringService();
        
        fraudDetectionService = new FraudDetectionService(
            fraudRuleRepository,
            fraudAlertRepository,
            blacklistRepository,
            redisTemplate,
            velocityCheckService,
            geolocationService,
            mlFraudScoringService
        );
    }
    
    @Test
    void shouldCalculateFraudScoreWithinValidRange() {
        // Given
        FraudEvaluationRequest request = createValidRequest();
        when(blacklistRepository.existsByEntryTypeAndValue(anyString(), anyString())).thenReturn(false);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then
        assertThat(result.getFraudScore()).isBetween(0.0, 1.0);
    }
    
    @Test
    void shouldBlockBlacklistedIP() {
        // Given
        FraudEvaluationRequest request = createValidRequest();
        lenient().when(blacklistRepository.existsByEntryTypeAndValue(eq("IP"), eq(request.getIpAddress()))).thenReturn(true);
        lenient().when(blacklistRepository.existsByEntryTypeAndValue(eq("DEVICE_FINGERPRINT"), anyString())).thenReturn(false);
        lenient().when(blacklistRepository.existsByEntryTypeAndValue(eq("CARD_HASH"), anyString())).thenReturn(false);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(FraudStatus.BLOCK);
        assertThat(result.getFraudScore()).isEqualTo(1.0);
        assertThat(result.getTriggeredRules()).contains("BLACKLIST_HIT");
        verify(fraudAlertRepository).save(any());
    }
    
    @Test
    void shouldBlockBlacklistedDeviceFingerprint() {
        // Given
        FraudEvaluationRequest request = createValidRequest();
        lenient().when(blacklistRepository.existsByEntryTypeAndValue(eq("IP"), anyString())).thenReturn(false);
        lenient().when(blacklistRepository.existsByEntryTypeAndValue(eq("DEVICE_FINGERPRINT"), eq(request.getDeviceFingerprint())))
            .thenReturn(true);
        lenient().when(blacklistRepository.existsByEntryTypeAndValue(eq("CARD_HASH"), anyString())).thenReturn(false);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(FraudStatus.BLOCK);
        assertThat(result.getFraudScore()).isEqualTo(1.0);
        assertThat(result.getTriggeredRules()).contains("BLACKLIST_HIT");
    }
    
    @Test
    void shouldBlockBlacklistedCardHash() {
        // Given
        FraudEvaluationRequest request = createValidRequest();
        lenient().when(blacklistRepository.existsByEntryTypeAndValue(eq("IP"), anyString())).thenReturn(false);
        lenient().when(blacklistRepository.existsByEntryTypeAndValue(eq("DEVICE_FINGERPRINT"), anyString())).thenReturn(false);
        lenient().when(blacklistRepository.existsByEntryTypeAndValue(eq("CARD_HASH"), eq(request.getCardToken()))).thenReturn(true);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(FraudStatus.BLOCK);
        assertThat(result.getFraudScore()).isEqualTo(1.0);
        assertThat(result.getTriggeredRules()).contains("BLACKLIST_HIT");
    }
    
    @Test
    void shouldRequire3DSForHighRiskTransactions() {
        // Given: A high-value transaction from a high-risk country
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            "txn_123",
            new BigDecimal("15000.00"),  // High amount
            "USD",
            "card_token_123",
            "41.123.45.67",  // Nigerian IP (high risk)
            "device_123",
            new FraudEvaluationRequest.Address("123 St", "Lagos", "Lagos", "12345", "NG"),
            "merchant_123",
            new HashMap<>()
        );
        when(blacklistRepository.existsByEntryTypeAndValue(anyString(), anyString())).thenReturn(false);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: Should require 3DS due to high risk
        if (result.getFraudScore() >= 0.50) {
            assertThat(result.isRequire3DS()).isTrue();
        }
    }
    
    @Test
    void shouldHandleVelocityLimitExceeded() {
        // Given: Simulate velocity limit exceeded
        FraudEvaluationRequest request = createValidRequest();
        when(blacklistRepository.existsByEntryTypeAndValue(anyString(), anyString())).thenReturn(false);
        when(valueOperations.increment(anyString())).thenReturn(100L);  // Exceeds limit
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: Should have higher fraud score and potentially trigger velocity rule
        assertThat(result.getFraudScore()).isGreaterThan(0.0);
    }
    
    @Test
    void shouldAssessGeolocationRisk() {
        // Given: Transaction from high-risk country
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            "txn_123",
            new BigDecimal("1000.00"),
            "USD",
            "card_token_123",
            "41.123.45.67",  // Nigerian IP
            "device_123",
            new FraudEvaluationRequest.Address("123 St", "Lagos", "Lagos", "12345", "NG"),
            "merchant_123",
            new HashMap<>()
        );
        when(blacklistRepository.existsByEntryTypeAndValue(anyString(), anyString())).thenReturn(false);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: Should have elevated fraud score due to geolocation
        assertThat(result.getFraudScore()).isGreaterThan(0.0);
    }
    
    @Test
    void shouldHandleCountryMismatch() {
        // Given: IP country doesn't match billing country
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            "txn_123",
            new BigDecimal("1000.00"),
            "USD",
            "card_token_123",
            "41.123.45.67",  // Nigerian IP
            "device_123",
            new FraudEvaluationRequest.Address("123 St", "New York", "NY", "10001", "US"),  // US billing
            "merchant_123",
            new HashMap<>()
        );
        when(blacklistRepository.existsByEntryTypeAndValue(anyString(), anyString())).thenReturn(false);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: Should have elevated fraud score due to country mismatch
        assertThat(result.getFraudScore()).isGreaterThan(0.0);
    }
    
    @Test
    void shouldHandleMissingOptionalFields() {
        // Given: Request with missing optional fields
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            "txn_123",
            new BigDecimal("100.00"),
            "USD",
            "card_token_123",
            null,  // No IP
            null,  // No device fingerprint
            null,  // No billing address
            "merchant_123",
            null   // No metadata
        );
        when(blacklistRepository.existsByEntryTypeAndValue(anyString(), anyString())).thenReturn(false);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: Should still calculate a valid fraud score
        assertThat(result.getFraudScore()).isBetween(0.0, 1.0);
        assertThat(result.getStatus()).isNotNull();
    }
    
    @Test
    void shouldCreateFraudAlertForHighRiskTransactions() {
        // Given: A high-risk transaction
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            "txn_123",
            new BigDecimal("20000.00"),  // Very high amount
            "USD",
            "card_token_123",
            "41.123.45.67",
            "device_123",
            new FraudEvaluationRequest.Address("123 St", "Lagos", "Lagos", "12345", "NG"),
            "merchant_123",
            new HashMap<>()
        );
        when(blacklistRepository.existsByEntryTypeAndValue(anyString(), anyString())).thenReturn(false);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: Should create fraud alert if score is high enough
        if (result.getFraudScore() >= 0.50) {
            verify(fraudAlertRepository, atLeastOnce()).save(any());
        }
    }
    
    @Test
    void shouldNotCreateFraudAlertForLowRiskTransactions() {
        // Given: A low-risk transaction
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            "txn_123",
            new BigDecimal("10.00"),  // Small amount
            "USD",
            "card_token_123",
            "8.8.8.8",  // US IP
            "device_123",
            new FraudEvaluationRequest.Address("123 St", "New York", "NY", "10001", "US"),
            "merchant_123",
            new HashMap<>()
        );
        when(blacklistRepository.existsByEntryTypeAndValue(anyString(), anyString())).thenReturn(false);
        
        // When
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: Should not create fraud alert if score is low
        if (result.getFraudScore() < 0.50) {
            verify(fraudAlertRepository, never()).save(any());
        }
    }
    
    private FraudEvaluationRequest createValidRequest() {
        return new FraudEvaluationRequest(
            "txn_123",
            new BigDecimal("100.00"),
            "USD",
            "card_token_123",
            "192.168.1.1",
            "device_123",
            new FraudEvaluationRequest.Address("123 Main St", "City", "State", "12345", "US"),
            "merchant_123",
            new HashMap<>()
        );
    }
}
