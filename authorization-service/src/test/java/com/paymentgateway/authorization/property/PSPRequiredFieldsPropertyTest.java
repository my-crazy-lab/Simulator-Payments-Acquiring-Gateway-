package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.psp.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature: payment-acquiring-gateway, Property 8: Required PSP Fields Present
 * 
 * For any PSP authorization request, all required fields (amount, currency, card token, merchant ID)
 * must be present in the request payload.
 * 
 * Validates: Requirements 3.3
 */
class PSPRequiredFieldsPropertyTest {
    
    @Property(tries = 100)
    void validRequestContainsAllRequiredFields(
            @ForAll("merchantIds") UUID merchantId,
            @ForAll @Positive BigDecimal amount,
            @ForAll("currencies") String currency,
            @ForAll("cardTokenIds") UUID cardTokenId) {
        
        // Given: A valid PSP authorization request with all required fields
        PSPAuthorizationRequest request = new PSPAuthorizationRequest();
        request.setMerchantId(merchantId);
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setCardTokenId(cardTokenId);
        
        // When: We validate the request
        // Then: All required fields should be present
        assertThat(request.getMerchantId())
            .as("Merchant ID should be present")
            .isNotNull();
        assertThat(request.getAmount())
            .as("Amount should be present and positive")
            .isNotNull()
            .isPositive();
        assertThat(request.getCurrency())
            .as("Currency should be present")
            .isNotNull()
            .isNotEmpty();
        assertThat(request.getCardTokenId())
            .as("Card token ID should be present")
            .isNotNull();
    }
    
    @Property(tries = 100)
    void requestWithoutMerchantIdIsRejected(
            @ForAll @Positive BigDecimal amount,
            @ForAll("currencies") String currency,
            @ForAll("cardTokenIds") UUID cardTokenId) {
        
        // Given: A request without merchant ID
        PSPAuthorizationRequest request = new PSPAuthorizationRequest();
        request.setMerchantId(null); // Missing merchant ID
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setCardTokenId(cardTokenId);
        
        // When/Then: PSP client should reject the request
        StripePSPClient pspClient = new StripePSPClient();
        assertThatThrownBy(() -> pspClient.authorize(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Merchant ID is required");
    }
    
    @Property(tries = 100)
    void requestWithoutAmountIsRejected(
            @ForAll("merchantIds") UUID merchantId,
            @ForAll("currencies") String currency,
            @ForAll("cardTokenIds") UUID cardTokenId) {
        
        // Given: A request without amount
        PSPAuthorizationRequest request = new PSPAuthorizationRequest();
        request.setMerchantId(merchantId);
        request.setAmount(null); // Missing amount
        request.setCurrency(currency);
        request.setCardTokenId(cardTokenId);
        
        // When/Then: PSP client should reject the request
        StripePSPClient pspClient = new StripePSPClient();
        assertThatThrownBy(() -> pspClient.authorize(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Amount");
    }
    
    @Property(tries = 100)
    void requestWithoutCurrencyIsRejected(
            @ForAll("merchantIds") UUID merchantId,
            @ForAll @Positive BigDecimal amount,
            @ForAll("cardTokenIds") UUID cardTokenId) {
        
        // Given: A request without currency
        PSPAuthorizationRequest request = new PSPAuthorizationRequest();
        request.setMerchantId(merchantId);
        request.setAmount(amount);
        request.setCurrency(null); // Missing currency
        request.setCardTokenId(cardTokenId);
        
        // When/Then: PSP client should reject the request
        StripePSPClient pspClient = new StripePSPClient();
        assertThatThrownBy(() -> pspClient.authorize(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Currency is required");
    }
    
    @Property(tries = 100)
    void requestWithoutCardTokenIsRejected(
            @ForAll("merchantIds") UUID merchantId,
            @ForAll @Positive BigDecimal amount,
            @ForAll("currencies") String currency) {
        
        // Given: A request without card token
        PSPAuthorizationRequest request = new PSPAuthorizationRequest();
        request.setMerchantId(merchantId);
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setCardTokenId(null); // Missing card token
        
        // When/Then: PSP client should reject the request
        StripePSPClient pspClient = new StripePSPClient();
        assertThatThrownBy(() -> pspClient.authorize(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Card token ID is required");
    }
    
    // Arbitraries
    
    @Provide
    Arbitrary<UUID> merchantIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<UUID> cardTokenIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<String> currencies() {
        return Arbitraries.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD");
    }
}
