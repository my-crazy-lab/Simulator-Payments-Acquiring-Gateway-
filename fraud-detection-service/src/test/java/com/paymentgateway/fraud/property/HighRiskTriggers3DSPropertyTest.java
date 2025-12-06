package com.paymentgateway.fraud.property;

import com.paymentgateway.fraud.domain.FraudStatus;
import com.paymentgateway.fraud.service.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Feature: payment-acquiring-gateway, Property 10: High Risk Triggers 3DS
 * For any transaction with fraud score above the configured threshold (e.g., 0.75), 
 * the system should require 3D Secure authentication.
 * Validates: Requirements 4.3
 */
class HighRiskTriggers3DSPropertyTest {
    
    private static final double HIGH_RISK_THRESHOLD = 0.75;
    
    private FraudDetectionService fraudDetectionService;
    private VelocityCheckService velocityCheckService;
    private GeolocationService geolocationService;
    private MLFraudScoringService mlFraudScoringService;
    
    @BeforeEach
    void setUp() {
        // Create mocks for dependencies
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        velocityCheckService = new VelocityCheckService(redisTemplate);
        geolocationService = new GeolocationService();
        mlFraudScoringService = new MLFraudScoringService();
        
        fraudDetectionService = new FraudDetectionService(
            mock(com.paymentgateway.fraud.repository.FraudRuleRepository.class),
            mock(com.paymentgateway.fraud.repository.FraudAlertRepository.class),
            mock(com.paymentgateway.fraud.repository.BlacklistRepository.class),
            redisTemplate,
            velocityCheckService,
            geolocationService,
            mlFraudScoringService
        );
        
        // Mock Redis operations to avoid actual Redis calls
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
    }
    
    @Property(tries = 100)
    void highRiskTransactionsShouldRequire3DS(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll @BigRange(min = "0.01", max = "100000.00") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: A fraud evaluation request
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            transactionId,
            amount,
            currency,
            cardToken,
            generateRandomIP(),
            generateRandomDeviceFingerprint(),
            generateRandomAddress(),
            merchantId,
            new HashMap<>()
        );
        
        // When: The transaction is evaluated for fraud
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: If fraud score is above threshold, 3DS should be required
        if (result.getFraudScore() >= HIGH_RISK_THRESHOLD) {
            assertThat(result.isRequire3DS())
                .as("Transactions with fraud score >= 0.75 must require 3DS")
                .isTrue();
            
            assertThat(result.getStatus())
                .as("High risk transactions should be BLOCK or REVIEW status")
                .isIn(FraudStatus.BLOCK, FraudStatus.REVIEW);
        }
    }
    
    @Property(tries = 100)
    void mediumRiskTransactionsShouldAlsoRequire3DS(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll @BigRange(min = "0.01", max = "100000.00") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: A fraud evaluation request
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            transactionId,
            amount,
            currency,
            cardToken,
            generateRandomIP(),
            generateRandomDeviceFingerprint(),
            generateRandomAddress(),
            merchantId,
            new HashMap<>()
        );
        
        // When: The transaction is evaluated for fraud
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: If fraud score is in medium risk range (0.50-0.75), 3DS should be required
        if (result.getFraudScore() >= 0.50 && result.getFraudScore() < HIGH_RISK_THRESHOLD) {
            assertThat(result.isRequire3DS())
                .as("Medium risk transactions (score 0.50-0.75) should require 3DS")
                .isTrue();
            
            assertThat(result.getStatus())
                .as("Medium risk transactions should be REVIEW status")
                .isEqualTo(FraudStatus.REVIEW);
        }
    }
    
    @Property(tries = 100)
    void lowRiskTransactionsShouldNotRequire3DS(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll @BigRange(min = "0.01", max = "100000.00") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: A fraud evaluation request
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            transactionId,
            amount,
            currency,
            cardToken,
            generateRandomIP(),
            generateRandomDeviceFingerprint(),
            generateRandomAddress(),
            merchantId,
            new HashMap<>()
        );
        
        // When: The transaction is evaluated for fraud
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: If fraud score is below 0.50, 3DS is not required
        if (result.getFraudScore() < 0.50) {
            assertThat(result.isRequire3DS())
                .as("Low risk transactions (score < 0.50) should not require 3DS")
                .isFalse();
            
            assertThat(result.getStatus())
                .as("Low risk transactions should be CLEAN status")
                .isEqualTo(FraudStatus.CLEAN);
        }
    }
    
    private String generateRandomIP() {
        int a = (int) (Math.random() * 255);
        int b = (int) (Math.random() * 255);
        int c = (int) (Math.random() * 255);
        int d = (int) (Math.random() * 255);
        return a + "." + b + "." + c + "." + d;
    }
    
    private String generateRandomDeviceFingerprint() {
        return "device_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
    }
    
    private FraudEvaluationRequest.Address generateRandomAddress() {
        String[] countries = {"US", "GB", "CA", "AU", "DE", "FR"};
        String country = countries[(int) (Math.random() * countries.length)];
        
        return new FraudEvaluationRequest.Address(
            "123 Main St",
            "City",
            "State",
            "12345",
            country
        );
    }
}
