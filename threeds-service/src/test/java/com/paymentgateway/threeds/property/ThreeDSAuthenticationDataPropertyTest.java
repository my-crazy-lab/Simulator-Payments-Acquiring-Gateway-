package com.paymentgateway.threeds.property;

import com.paymentgateway.threeds.domain.BrowserInfo;
import com.paymentgateway.threeds.domain.ThreeDSStatus;
import com.paymentgateway.threeds.domain.ThreeDSTransaction;
import com.paymentgateway.threeds.service.ACSSimulator;
import com.paymentgateway.threeds.service.ThreeDSService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Feature: payment-acquiring-gateway, Property 12: 3DS Authentication Data Included
 * 
 * For any successfully authenticated 3D Secure transaction, the authorization request 
 * to the PSP must include CAVV, ECI, and XID values.
 * 
 * Validates: Requirements 5.4
 */
class ThreeDSAuthenticationDataPropertyTest {

    @Property(tries = 100)
    void authenticatedTransactionsShouldIncludeAllAuthenticationData(
            @ForAll @StringLength(min = 10, max = 50) String transactionId,
            @ForAll @StringLength(min = 5, max = 20) String merchantId,
            @ForAll("validAmounts") BigDecimal amount,
            @ForAll("currencies") String currency,
            @ForAll @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @StringLength(min = 10, max = 100) String merchantReturnUrl,
            @ForAll("browserInfo") BrowserInfo browserInfo) {

        // Setup mocks
        @SuppressWarnings("unchecked")
        RedisTemplate<String, ThreeDSTransaction> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, ThreeDSTransaction> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        ACSSimulator acsSimulator = mock(ACSSimulator.class);
        when(acsSimulator.getAcsUrl()).thenReturn("http://test-acs.example.com");
        when(acsSimulator.validatePARes(anyString())).thenReturn(true);

        ThreeDSService service = new ThreeDSService(redisTemplate, acsSimulator);

        // Initiate authentication
        ThreeDSTransaction transaction = service.initiateAuthentication(
            transactionId, merchantId, amount, currency, cardToken, 
            merchantReturnUrl, browserInfo
        );

        // For frictionless flow, authentication data should be immediately available
        if (transaction.getStatus() == ThreeDSStatus.FRICTIONLESS) {
            assertThat(transaction.getCavv())
                .as("CAVV must be present for frictionless authentication")
                .isNotNull()
                .isNotEmpty();
            
            assertThat(transaction.getEci())
                .as("ECI must be present for frictionless authentication")
                .isNotNull()
                .isNotEmpty()
                .matches("\\d{2}"); // ECI is a 2-digit code
            
            assertThat(transaction.getXid())
                .as("XID must be present for frictionless authentication")
                .isNotNull()
                .isNotEmpty();
        }

        // For challenge flow, complete the authentication
        if (transaction.getStatus() == ThreeDSStatus.CHALLENGE_REQUIRED) {
            // Mock Redis to return the transaction when retrieved
            when(valueOps.get(anyString())).thenReturn(transaction);
            
            String mockPARes = "mock-pares-success";
            ThreeDSTransaction completedTransaction = service.completeAuthentication(
                transactionId, mockPARes
            );

            // After successful authentication, all data must be present
            if (completedTransaction.getStatus() == ThreeDSStatus.AUTHENTICATED) {
                assertThat(completedTransaction.getCavv())
                    .as("CAVV must be present after successful authentication")
                    .isNotNull()
                    .isNotEmpty();
                
                assertThat(completedTransaction.getEci())
                    .as("ECI must be present after successful authentication")
                    .isNotNull()
                    .isNotEmpty()
                    .matches("\\d{2}");
                
                assertThat(completedTransaction.getXid())
                    .as("XID must be present after successful authentication")
                    .isNotNull()
                    .isNotEmpty();
            }
        }
    }

    @Property(tries = 100)
    void failedAuthenticationsShouldNotIncludeCavv(
            @ForAll @StringLength(min = 10, max = 50) String transactionId,
            @ForAll @StringLength(min = 5, max = 20) String merchantId,
            @ForAll("validAmounts") BigDecimal amount,
            @ForAll("currencies") String currency,
            @ForAll @StringLength(min = 16, max = 32) String cardToken,
            @ForAll @StringLength(min = 10, max = 100) String merchantReturnUrl,
            @ForAll("browserInfo") BrowserInfo browserInfo) {

        // Setup mocks for failed authentication
        @SuppressWarnings("unchecked")
        RedisTemplate<String, ThreeDSTransaction> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, ThreeDSTransaction> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        ACSSimulator acsSimulator = mock(ACSSimulator.class);
        when(acsSimulator.getAcsUrl()).thenReturn("http://test-acs.example.com");
        when(acsSimulator.validatePARes(anyString())).thenReturn(false); // Simulate failure

        ThreeDSService service = new ThreeDSService(redisTemplate, acsSimulator);

        // Initiate and complete with failed authentication
        ThreeDSTransaction transaction = service.initiateAuthentication(
            transactionId, merchantId, amount, currency, cardToken, 
            merchantReturnUrl, browserInfo
        );

        if (transaction.getStatus() == ThreeDSStatus.CHALLENGE_REQUIRED) {
            // Mock Redis to return the transaction when retrieved
            when(valueOps.get(anyString())).thenReturn(transaction);
            
            String mockPARes = "mock-pares-failed";
            ThreeDSTransaction completedTransaction = service.completeAuthentication(
                transactionId, mockPARes
            );

            // Failed authentication should not have CAVV
            if (completedTransaction.getStatus() == ThreeDSStatus.FAILED) {
                assertThat(completedTransaction.getCavv())
                    .as("CAVV should not be present for failed authentication")
                    .isNullOrEmpty();
            }
        }
    }

    @Provide
    Arbitrary<BigDecimal> validAmounts() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(10000.00))
            .ofScale(2);
    }

    @Provide
    Arbitrary<String> currencies() {
        return Arbitraries.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD");
    }

    @Provide
    Arbitrary<BrowserInfo> browserInfo() {
        // Split into two combines due to 8-parameter limit
        Arbitrary<String> userAgent = Arbitraries.strings().alpha().ofLength(50);
        Arbitrary<String> acceptHeader = Arbitraries.strings().alpha().ofLength(20);
        Arbitrary<String> language = Arbitraries.of("en-US", "en-GB", "fr-FR", "de-DE");
        Arbitrary<Integer> screenWidth = Arbitraries.integers().between(800, 3840);
        Arbitrary<Integer> screenHeight = Arbitraries.integers().between(600, 2160);
        Arbitrary<Integer> colorDepth = Arbitraries.integers().between(8, 32);
        Arbitrary<String> timezoneOffset = Arbitraries.of("-300", "-240", "0", "+60", "+120");
        Arbitrary<Boolean> javaEnabled = Arbitraries.of(true, false);
        Arbitrary<Boolean> javascriptEnabled = Arbitraries.of(true, false);
        Arbitrary<String> ipAddress = Arbitraries.strings().numeric().ofLength(12);
        
        return Combinators.combine(
            userAgent, acceptHeader, language, screenWidth, screenHeight, 
            colorDepth, timezoneOffset, javaEnabled
        ).flatAs((ua, ah, lang, sw, sh, cd, tz, je) ->
            Combinators.combine(javascriptEnabled, ipAddress)
                .as((jse, ip) -> new BrowserInfo(ua, ah, lang, sw, sh, cd, tz, je, jse, ip))
        );
    }
}
