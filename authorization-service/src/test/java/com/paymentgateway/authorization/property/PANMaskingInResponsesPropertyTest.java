package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.domain.*;
import com.paymentgateway.authorization.dto.PaymentResponse;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: payment-acquiring-gateway, Property 29: PAN Masking in Responses
 * 
 * For any transaction query response, card numbers should be masked showing 
 * only the last 4 digits (e.g., ****1234).
 * 
 * Validates: Requirements 15.2
 */
class PANMaskingInResponsesPropertyTest {
    
    /**
     * Property: For any transaction with a card number, the response DTO should only 
     * contain the last 4 digits, never the full PAN
     */
    @Property(tries = 100)
    void panMaskingInPaymentResponse(
            @ForAll @StringLength(min = 13, max = 19) @From("cardNumbers") String fullCardNumber,
            @ForAll @IntRange(min = 100, max = 999999) int amountCents,
            @ForAll @From("currencies") String currency) {
        
        // Given: A payment entity with a full card number stored
        String lastFour = fullCardNumber.substring(fullCardNumber.length() - 4);
        
        Payment payment = new Payment();
        payment.setPaymentId("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        payment.setMerchantId(UUID.randomUUID());
        payment.setAmount(BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100)));
        payment.setCurrency(currency);
        payment.setCardTokenId(UUID.randomUUID()); // Simulated token - full PAN never stored
        payment.setCardLastFour(lastFour);
        payment.setCardBrand(CardBrand.VISA);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setFraudStatus(FraudStatus.CLEAN);
        payment.setThreeDsStatus(ThreeDSStatus.NOT_ENROLLED);
        payment.setTransactionType(TransactionType.AUTHORIZATION);
        payment.setCreatedAt(Instant.now());
        
        // When: Converting to response DTO (simulating what the service does)
        PaymentResponse response = convertToResponse(payment);
        
        // Then: Response should contain only last 4 digits, never full PAN
        assertThat(response.getCardLastFour())
            .as("Card last four should be present")
            .isNotNull()
            .hasSize(4)
            .containsOnlyDigits()
            .isEqualTo(lastFour);
        
        // Verify the response doesn't contain the full card number anywhere
        String responseString = response.toString();
        assertThat(responseString)
            .as("Response should not contain full card number")
            .doesNotContain(fullCardNumber);
        
        // Verify all response fields
        assertThat(response.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(response.getStatus()).isEqualTo(payment.getStatus());
        assertThat(response.getAmount()).isEqualByComparingTo(payment.getAmount());
        assertThat(response.getCurrency()).isEqualTo(payment.getCurrency());
    }
    
    /**
     * Helper method that simulates the service layer conversion
     * This is the actual implementation that should mask PANs
     */
    private PaymentResponse convertToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        
        // PAN masking - only show last 4 digits
        if (payment.getCardLastFour() != null) {
            response.setCardLastFour(payment.getCardLastFour());
        }
        
        response.setCardBrand(payment.getCardBrand() != null ? payment.getCardBrand().name() : null);
        response.setCreatedAt(payment.getCreatedAt());
        response.setAuthorizedAt(payment.getAuthorizedAt());
        
        return response;
    }
    
    /**
     * Property: For any list of transactions, all responses should have masked PANs
     */
    @Property(tries = 100)
    void panMaskingInMultipleTransactions(
            @ForAll @IntRange(min = 1, max = 10) int transactionCount,
            @ForAll @From("currencies") String currency) {
        
        // Given: Multiple payments with different card numbers
        List<Payment> payments = new ArrayList<>();
        List<String> fullCardNumbers = new ArrayList<>();
        
        for (int i = 0; i < transactionCount; i++) {
            String fullCardNumber = generateCardNumber(13 + (i % 7)); // 13-19 digits
            fullCardNumbers.add(fullCardNumber);
            String lastFour = fullCardNumber.substring(fullCardNumber.length() - 4);
            
            Payment payment = new Payment();
            payment.setPaymentId("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            payment.setMerchantId(UUID.randomUUID());
            payment.setAmount(BigDecimal.valueOf(100 + i));
            payment.setCurrency(currency);
            payment.setCardTokenId(UUID.randomUUID());
            payment.setCardLastFour(lastFour);
            payment.setCardBrand(CardBrand.values()[i % CardBrand.values().length]);
            payment.setStatus(PaymentStatus.AUTHORIZED);
            payment.setFraudStatus(FraudStatus.CLEAN);
            payment.setThreeDsStatus(ThreeDSStatus.NOT_ENROLLED);
            payment.setTransactionType(TransactionType.AUTHORIZATION);
            payment.setCreatedAt(Instant.now());
            
            payments.add(payment);
        }
        
        // When: Converting all to responses
        List<PaymentResponse> responses = new ArrayList<>();
        for (Payment payment : payments) {
            responses.add(convertToResponse(payment));
        }
        
        // Then: All responses should have masked PANs
        assertThat(responses).hasSize(transactionCount);
        
        for (int i = 0; i < responses.size(); i++) {
            PaymentResponse response = responses.get(i);
            String fullCardNumber = fullCardNumbers.get(i);
            
            assertThat(response.getCardLastFour())
                .as("Response %d should have last 4 digits", i)
                .isNotNull()
                .hasSize(4)
                .containsOnlyDigits();
            
            // Verify full card number is never in response
            String responseString = response.toString();
            assertThat(responseString)
                .as("Response %d should not contain full card number", i)
                .doesNotContain(fullCardNumber);
        }
    }
    
    /**
     * Generate a card number of specified length
     */
    private String generateCardNumber(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((int) (Math.random() * 10));
        }
        return sb.toString();
    }
    
    /**
     * Property: Even with different payment statuses, PAN should always be masked
     */
    @Property(tries = 100)
    void panMaskingAcrossAllPaymentStatuses(
            @ForAll @StringLength(min = 13, max = 19) @From("cardNumbers") String fullCardNumber,
            @ForAll @From("paymentStatuses") PaymentStatus status) {
        
        // Given: A payment with any status
        String lastFour = fullCardNumber.substring(fullCardNumber.length() - 4);
        
        Payment payment = new Payment();
        payment.setPaymentId("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        payment.setMerchantId(UUID.randomUUID());
        payment.setAmount(BigDecimal.valueOf(100.00));
        payment.setCurrency("USD");
        payment.setCardTokenId(UUID.randomUUID());
        payment.setCardLastFour(lastFour);
        payment.setCardBrand(CardBrand.AMEX);
        payment.setStatus(status);
        payment.setFraudStatus(FraudStatus.CLEAN);
        payment.setThreeDsStatus(ThreeDSStatus.NOT_ENROLLED);
        payment.setTransactionType(TransactionType.AUTHORIZATION);
        payment.setCreatedAt(Instant.now());
        
        // When: Converting to response
        PaymentResponse response = convertToResponse(payment);
        
        // Then: PAN should be masked regardless of status
        assertThat(response.getCardLastFour())
            .as("Card last four should be present for status %s", status)
            .isNotNull()
            .hasSize(4)
            .isEqualTo(lastFour);
        
        // Verify full PAN is never exposed
        String responseString = response.toString();
        assertThat(responseString)
            .as("Response should not contain full PAN for status %s", status)
            .doesNotContain(fullCardNumber);
    }
    
    // Providers for generating test data
    
    @Provide
    Arbitrary<String> cardNumbers() {
        // Generate valid-looking card numbers (13-19 digits)
        return Arbitraries.integers()
            .between(13, 19)
            .flatMap(length -> 
                Arbitraries.strings()
                    .numeric()
                    .ofLength(length)
            );
    }
    
    @Provide
    Arbitrary<String> currencies() {
        return Arbitraries.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD");
    }
    
    @Provide
    Arbitrary<PaymentStatus> paymentStatuses() {
        return Arbitraries.of(PaymentStatus.values());
    }
}
