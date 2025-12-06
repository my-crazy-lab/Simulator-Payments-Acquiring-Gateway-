package com.paymentgateway.fraud.property;

import com.paymentgateway.fraud.domain.Blacklist;
import com.paymentgateway.fraud.domain.FraudStatus;
import com.paymentgateway.fraud.repository.BlacklistRepository;
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
 * Feature: payment-acquiring-gateway, Property 11: Blacklist Immediate Rejection
 * For any transaction from a source on the blacklist (IP, card hash, device fingerprint), 
 * the transaction should be rejected before authorization is attempted.
 * Validates: Requirements 4.5
 */
class BlacklistImmediateRejectionPropertyTest {
    
    private FraudDetectionService fraudDetectionService;
    private BlacklistRepository blacklistRepository;
    private VelocityCheckService velocityCheckService;
    private GeolocationService geolocationService;
    private MLFraudScoringService mlFraudScoringService;
    
    @net.jqwik.api.lifecycle.BeforeProperty
    void setUp() {
        // Create mocks for dependencies
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        blacklistRepository = mock(BlacklistRepository.class);
        velocityCheckService = new VelocityCheckService(redisTemplate);
        geolocationService = new GeolocationService();
        mlFraudScoringService = new MLFraudScoringService();
        
        fraudDetectionService = new FraudDetectionService(
            mock(com.paymentgateway.fraud.repository.FraudRuleRepository.class),
            mock(com.paymentgateway.fraud.repository.FraudAlertRepository.class),
            blacklistRepository,
            redisTemplate,
            velocityCheckService,
            geolocationService,
            mlFraudScoringService
        );
        
        // Mock Redis operations to avoid actual Redis calls
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
    }
    
    @Property(tries = 100)
    void blacklistedIPShouldBeRejectedImmediately(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll @BigRange(min = "0.01", max = "100000.00") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: A blacklisted IP address
        String blacklistedIP = generateRandomIP();
        when(blacklistRepository.existsByEntryTypeAndValue("IP", blacklistedIP))
            .thenReturn(true);
        
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            transactionId,
            amount,
            currency,
            cardToken,
            blacklistedIP,  // Blacklisted IP
            generateRandomDeviceFingerprint(),
            generateRandomAddress(),
            merchantId,
            new HashMap<>()
        );
        
        // When: The transaction is evaluated for fraud
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: The transaction should be blocked immediately
        assertThat(result.getStatus())
            .as("Transactions from blacklisted IP should be BLOCKED")
            .isEqualTo(FraudStatus.BLOCK);
        
        assertThat(result.getFraudScore())
            .as("Blacklisted transactions should have maximum fraud score")
            .isEqualTo(1.0);
        
        assertThat(result.getTriggeredRules())
            .as("Blacklist rule should be triggered")
            .contains("BLACKLIST_HIT");
    }
    
    @Property(tries = 100)
    void blacklistedDeviceFingerprintShouldBeRejectedImmediately(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll @BigRange(min = "0.01", max = "100000.00") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: A blacklisted device fingerprint
        String blacklistedDevice = generateRandomDeviceFingerprint();
        when(blacklistRepository.existsByEntryTypeAndValue("DEVICE_FINGERPRINT", blacklistedDevice))
            .thenReturn(true);
        
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            transactionId,
            amount,
            currency,
            cardToken,
            generateRandomIP(),
            blacklistedDevice,  // Blacklisted device
            generateRandomAddress(),
            merchantId,
            new HashMap<>()
        );
        
        // When: The transaction is evaluated for fraud
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: The transaction should be blocked immediately
        assertThat(result.getStatus())
            .as("Transactions from blacklisted device should be BLOCKED")
            .isEqualTo(FraudStatus.BLOCK);
        
        assertThat(result.getFraudScore())
            .as("Blacklisted transactions should have maximum fraud score")
            .isEqualTo(1.0);
        
        assertThat(result.getTriggeredRules())
            .as("Blacklist rule should be triggered")
            .contains("BLACKLIST_HIT");
    }
    
    @Property(tries = 100)
    void blacklistedCardHashShouldBeRejectedImmediately(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll @BigRange(min = "0.01", max = "100000.00") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: A blacklisted card hash
        when(blacklistRepository.existsByEntryTypeAndValue("CARD_HASH", cardToken))
            .thenReturn(true);
        
        FraudEvaluationRequest request = new FraudEvaluationRequest(
            transactionId,
            amount,
            currency,
            cardToken,  // Blacklisted card
            generateRandomIP(),
            generateRandomDeviceFingerprint(),
            generateRandomAddress(),
            merchantId,
            new HashMap<>()
        );
        
        // When: The transaction is evaluated for fraud
        FraudEvaluationResult result = fraudDetectionService.evaluateTransaction(request);
        
        // Then: The transaction should be blocked immediately
        assertThat(result.getStatus())
            .as("Transactions from blacklisted card should be BLOCKED")
            .isEqualTo(FraudStatus.BLOCK);
        
        assertThat(result.getFraudScore())
            .as("Blacklisted transactions should have maximum fraud score")
            .isEqualTo(1.0);
        
        assertThat(result.getTriggeredRules())
            .as("Blacklist rule should be triggered")
            .contains("BLACKLIST_HIT");
    }
    
    @Property(tries = 100)
    void nonBlacklistedSourcesShouldNotBeAutomaticallyRejected(
            @ForAll @AlphaChars @StringLength(min = 10, max = 20) String transactionId,
            @ForAll @BigRange(min = "0.01", max = "100000.00") BigDecimal amount,
            @ForAll @StringLength(3) String currency,
            @ForAll @AlphaChars @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String merchantId) {
        
        // Given: No blacklisted sources
        when(blacklistRepository.existsByEntryTypeAndValue(anyString(), anyString()))
            .thenReturn(false);
        
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
        
        // Then: The transaction should not be automatically blocked due to blacklist
        assertThat(result.getTriggeredRules())
            .as("Non-blacklisted transactions should not trigger blacklist rule")
            .doesNotContain("BLACKLIST_HIT");
        
        // The transaction may still be blocked for other reasons, but not due to blacklist
        if (result.getStatus() == FraudStatus.BLOCK) {
            assertThat(result.getFraudScore())
                .as("If blocked for other reasons, score should be based on other factors")
                .isLessThan(1.0);
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
