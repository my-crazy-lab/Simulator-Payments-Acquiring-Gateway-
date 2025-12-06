package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.dto.PaymentRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Feature: payment-acquiring-gateway, Property 36: Input Validation
 * 
 * For any payment request, all required fields must be present and valid 
 * according to defined schemas before processing begins.
 * 
 * Validates: Requirements 18.1
 */
public class InputValidationPropertyTest {
    
    private static final Validator validator;
    
    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    /**
     * Property: Valid payment requests should pass all validation checks.
     */
    @Property(tries = 100)
    void validPaymentRequestsShouldPassValidation(
            @ForAll("validCardNumbers") String cardNumber,
            @ForAll @IntRange(min = 1, max = 12) int expiryMonth,
            @ForAll @IntRange(min = 2026, max = 2035) int expiryYear,
            @ForAll("validCvv") String cvv,
            @ForAll("validAmounts") BigDecimal amount,
            @ForAll("validCurrencies") String currency) {
        
        PaymentRequest request = new PaymentRequest(
            cardNumber, expiryMonth, expiryYear, cvv, amount, currency
        );
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        // Valid requests should have no violations
        if (!violations.isEmpty()) {
            System.out.println("Unexpected violations for valid request:");
            violations.forEach(v -> System.out.println("  " + v.getPropertyPath() + ": " + v.getMessage()));
        }
        
        assert violations.isEmpty() : "Valid payment request should not have validation errors";
    }
    
    /**
     * Property: Payment requests with missing required fields should fail validation.
     */
    @Property(tries = 100)
    void missingRequiredFieldsShouldFailValidation() {
        PaymentRequest request = new PaymentRequest();
        // All fields are null
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        // Should have violations for required fields
        assert !violations.isEmpty() : "Request with missing fields should have validation errors";
    }
    
    /**
     * Property: Payment requests with invalid card numbers should fail validation.
     */
    @Property(tries = 100)
    void invalidCardNumbersShouldFailValidation(
            @ForAll("invalidCardNumbers") String invalidCardNumber,
            @ForAll @IntRange(min = 1, max = 12) int expiryMonth,
            @ForAll @IntRange(min = 2026, max = 2035) int expiryYear,
            @ForAll("validCvv") String cvv,
            @ForAll("validAmounts") BigDecimal amount,
            @ForAll("validCurrencies") String currency) {
        
        PaymentRequest request = new PaymentRequest(
            invalidCardNumber, expiryMonth, expiryYear, cvv, amount, currency
        );
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        // Should have violations for card number
        boolean hasCardNumberViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("cardNumber"));
        
        assert hasCardNumberViolation : "Invalid card number should fail validation";
    }
    
    /**
     * Property: Payment requests with invalid amounts should fail validation.
     */
    @Property(tries = 100)
    void invalidAmountsShouldFailValidation(
            @ForAll("validCardNumbers") String cardNumber,
            @ForAll @IntRange(min = 1, max = 12) int expiryMonth,
            @ForAll @IntRange(min = 2026, max = 2035) int expiryYear,
            @ForAll("validCvv") String cvv,
            @ForAll("invalidAmounts") BigDecimal invalidAmount,
            @ForAll("validCurrencies") String currency) {
        
        PaymentRequest request = new PaymentRequest(
            cardNumber, expiryMonth, expiryYear, cvv, invalidAmount, currency
        );
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        // Should have violations for amount
        boolean hasAmountViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
        
        assert hasAmountViolation : "Invalid amount should fail validation";
    }
    
    /**
     * Property: Payment requests with invalid currency codes should fail validation.
     */
    @Property(tries = 100)
    void invalidCurrenciesShouldFailValidation(
            @ForAll("validCardNumbers") String cardNumber,
            @ForAll @IntRange(min = 1, max = 12) int expiryMonth,
            @ForAll @IntRange(min = 2026, max = 2035) int expiryYear,
            @ForAll("validCvv") String cvv,
            @ForAll("validAmounts") BigDecimal amount,
            @ForAll("invalidCurrencies") String invalidCurrency) {
        
        PaymentRequest request = new PaymentRequest(
            cardNumber, expiryMonth, expiryYear, cvv, amount, invalidCurrency
        );
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        // Should have violations for currency
        boolean hasCurrencyViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
        
        assert hasCurrencyViolation : "Invalid currency should fail validation";
    }
    
    // Generators
    
    @Provide
    Arbitrary<String> validCardNumbers() {
        // Generate valid card numbers that pass Luhn check
        return Arbitraries.of(
            "4532015112830366", // Visa
            "5425233430109903", // Mastercard
            "374245455400126",  // Amex
            "6011111111111117", // Discover
            "3530111333300000"  // JCB
        );
    }
    
    @Provide
    Arbitrary<String> invalidCardNumbers() {
        return Arbitraries.oneOf(
            Arbitraries.strings().alpha().ofLength(16), // Letters
            Arbitraries.strings().numeric().ofLength(10), // Too short
            Arbitraries.strings().numeric().ofLength(20), // Too long
            Arbitraries.just("4532015112830367") // Fails Luhn check (last digit changed)
        );
    }
    
    @Provide
    Arbitrary<String> validCvv() {
        return Arbitraries.strings().numeric().ofMinLength(3).ofMaxLength(4);
    }
    
    @Provide
    Arbitrary<BigDecimal> validAmounts() {
        return Arbitraries.bigDecimals()
            .between(new BigDecimal("0.01"), new BigDecimal("999999.99"))
            .ofScale(2);
    }
    
    @Provide
    Arbitrary<BigDecimal> invalidAmounts() {
        return Arbitraries.oneOf(
            Arbitraries.just(BigDecimal.ZERO), // Too small
            Arbitraries.just(new BigDecimal("-10.00")), // Negative
            Arbitraries.just(new BigDecimal("1000000000.00")), // Too large
            Arbitraries.just(new BigDecimal("10.123")) // Too many decimal places
        );
    }
    
    @Provide
    Arbitrary<String> validCurrencies() {
        return Arbitraries.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF");
    }
    
    @Provide
    Arbitrary<String> invalidCurrencies() {
        return Arbitraries.oneOf(
            Arbitraries.just("ZZZ"), // Invalid code
            Arbitraries.just("AAA"), // Invalid code
            Arbitraries.just("US"), // Too short
            Arbitraries.just("USDD"), // Too long
            Arbitraries.just("123"), // Numbers
            Arbitraries.just("usd") // Lowercase
        );
    }
}
