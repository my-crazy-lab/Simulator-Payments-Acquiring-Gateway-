package com.paymentgateway.fraud.property;

import com.paymentgateway.fraud.service.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Feature: payment-acquiring-gateway, Property 9: Fraud Score Range
 * For any transaction evaluated by the fraud detection service, 
 * the fraud score must be a decimal value between 0.00 and 1.00 inclusive.
 * Validates: Requirements 4.1
 */
class FraudScoreRangePropertyTest {
    
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
    void fraudScoreMustBeInValidRange(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll @BigRange(min = "0.01", max = "100000.00") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: A fraud evaluation request with random valid inputs
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
        
        // Then: The fraud score must be between 0.0 and 1.0 inclusive
        assertThat(result.getFraudScore())
            .as("Fraud score must be between 0.0 and 1.0")
            .isGreaterThanOrEqualTo(0.0)
            .isLessThanOrEqualTo(1.0);
    }
    
    @Property(tries = 100)
    void fraudScoreMustBeValidEvenWithMissingOptionalFields(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll @BigRange(min = "0.01", max = "100000.00") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: A fraud evaluation request with missing optional fields
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            transactionId,
            amount,
            currency,
            cardToken,
            null,  // No IP address
            null,  // No device fingerprint
            null,  // No billing address
            merchantId,
            null   // No metadata
        );
        
        // When: The transaction is evaluated for fraud
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: The fraud score must still be in valid range
        assertThat(result.getFraudScore())
            .as("Fraud score must be between 0.0 and 1.0 even with missing fields")
            .isGreaterThanOrEqualTo(0.0)
            .isLessThanOrEqualTo(1.0);
    }
    
    @Property(tries = 100)
    void fraudScoreMustBeValidForEdgeAmounts(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll("edgeAmounts") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: A fraud evaluation request with edge case amounts
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
        
        // Then: The fraud score must be in valid range
        assertThat(result.getFraudScore())
            .as("Fraud score must be between 0.0 and 1.0 for edge amounts")
            .isGreaterThanOrEqualTo(0.0)
            .isLessThanOrEqualTo(1.0);
    }
    
    @Provide
    Arbitrary<BigDecimal> edgeAmounts() {
        return Arbitraries.of(
            new BigDecimal("0.01"),      // Minimum
            new BigDecimal("1.00"),      // Small
            new BigDecimal("999.99"),    // Just under 1000
            new BigDecimal("1000.00"),   // Threshold
            new BigDecimal("4999.99"),   // Just under 5000
            new BigDecimal("5000.00"),   // Threshold
            new BigDecimal("9999.99"),   // Just under 10000
            new BigDecimal("10000.00"),  // High threshold
            new BigDecimal("50000.00"),  // Very high
            new BigDecimal("100000.00")  // Maximum
        );
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
        String[] countries = {"US", "GB", "CA", "AU", "DE", "FR", "NG", "GH"};
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
