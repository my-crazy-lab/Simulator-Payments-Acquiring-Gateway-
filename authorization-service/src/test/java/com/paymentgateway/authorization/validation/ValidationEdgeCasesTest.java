package com.paymentgateway.authorization.validation;

import com.paymentgateway.authorization.dto.PaymentRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for validation edge cases.
 * Tests boundary values, invalid formats, and missing required fields.
 */
public class ValidationEdgeCasesTest {
    
    private static Validator validator;
    
    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    // Boundary value tests
    
    @Test
    void shouldAcceptMinimumValidAmount() {
        PaymentRequest request = createValidRequest();
        request.setAmount(new BigDecimal("0.01"));
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }
    
    @Test
    void shouldRejectAmountBelowMinimum() {
        PaymentRequest request = createValidRequest();
        request.setAmount(new BigDecimal("0.00"));
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }
    
    @Test
    void shouldAcceptMaximumValidAmount() {
        PaymentRequest request = createValidRequest();
        request.setAmount(new BigDecimal("999999999.99"));
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }
    
    @Test
    void shouldRejectAmountAboveMaximum() {
        PaymentRequest request = createValidRequest();
        request.setAmount(new BigDecimal("1000000000.00"));
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }
    
    @Test
    void shouldRejectNegativeAmount() {
        PaymentRequest request = createValidRequest();
        request.setAmount(new BigDecimal("-10.00"));
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }
    
    @Test
    void shouldRejectAmountWithTooManyDecimalPlaces() {
        PaymentRequest request = createValidRequest();
        request.setAmount(new BigDecimal("10.123"));
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }
    
    // Expiry date boundary tests
    
    @Test
    void shouldAcceptCurrentMonth() {
        PaymentRequest request = createValidRequest();
        YearMonth now = YearMonth.now();
        request.setExpiryMonth(now.getMonthValue());
        request.setExpiryYear(now.getYear());
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }
    
    @Test
    void shouldRejectPastMonth() {
        PaymentRequest request = createValidRequest();
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        request.setExpiryMonth(lastMonth.getMonthValue());
        request.setExpiryYear(lastMonth.getYear());
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
    }
    
    @Test
    void shouldRejectInvalidMonth() {
        PaymentRequest request = createValidRequest();
        request.setExpiryMonth(13);
        request.setExpiryYear(2025);
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
    }
    
    @Test
    void shouldRejectZeroMonth() {
        PaymentRequest request = createValidRequest();
        request.setExpiryMonth(0);
        request.setExpiryYear(2025);
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
    }
    
    // Invalid format tests
    
    @Test
    void shouldRejectCardNumberWithLetters() {
        PaymentRequest request = createValidRequest();
        request.setCardNumber("453201511283ABCD");
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cardNumber"));
    }
    
    @Test
    void shouldRejectCardNumberTooShort() {
        PaymentRequest request = createValidRequest();
        request.setCardNumber("123456789012"); // 12 digits
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cardNumber"));
    }
    
    @Test
    void shouldRejectCardNumberTooLong() {
        PaymentRequest request = createValidRequest();
        request.setCardNumber("12345678901234567890"); // 20 digits
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cardNumber"));
    }
    
    @Test
    void shouldRejectCardNumberFailingLuhnCheck() {
        PaymentRequest request = createValidRequest();
        request.setCardNumber("4532015112830367"); // Last digit wrong
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
            v.getPropertyPath().toString().equals("cardNumber") &&
            v.getMessage().contains("Luhn"));
    }
    
    @Test
    void shouldRejectInvalidCvvFormat() {
        PaymentRequest request = createValidRequest();
        request.setCvv("12"); // Too short
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cvv"));
    }
    
    @Test
    void shouldRejectCvvWithLetters() {
        PaymentRequest request = createValidRequest();
        request.setCvv("12A");
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cvv"));
    }
    
    @Test
    void shouldRejectInvalidCurrencyCode() {
        PaymentRequest request = createValidRequest();
        request.setCurrency("ZZZ"); // Invalid ISO code
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }
    
    @Test
    void shouldRejectLowercaseCurrency() {
        PaymentRequest request = createValidRequest();
        request.setCurrency("usd"); // Should be uppercase
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }
    
    @Test
    void shouldRejectTwoLetterCurrency() {
        PaymentRequest request = createValidRequest();
        request.setCurrency("US"); // Too short
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }
    
    // Missing required fields tests
    
    @Test
    void shouldRejectMissingCardNumber() {
        PaymentRequest request = createValidRequest();
        request.setCardNumber(null);
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cardNumber"));
    }
    
    @Test
    void shouldRejectEmptyCardNumber() {
        PaymentRequest request = createValidRequest();
        request.setCardNumber("");
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cardNumber"));
    }
    
    @Test
    void shouldRejectMissingExpiryMonth() {
        PaymentRequest request = createValidRequest();
        request.setExpiryMonth(null);
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("expiryMonth"));
    }
    
    @Test
    void shouldRejectMissingExpiryYear() {
        PaymentRequest request = createValidRequest();
        request.setExpiryYear(null);
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("expiryYear"));
    }
    
    @Test
    void shouldRejectMissingCvv() {
        PaymentRequest request = createValidRequest();
        request.setCvv(null);
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cvv"));
    }
    
    @Test
    void shouldRejectMissingAmount() {
        PaymentRequest request = createValidRequest();
        request.setAmount(null);
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }
    
    @Test
    void shouldRejectMissingCurrency() {
        PaymentRequest request = createValidRequest();
        request.setCurrency(null);
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currency"));
    }
    
    @Test
    void shouldRejectAllMissingFields() {
        PaymentRequest request = new PaymentRequest();
        
        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations.size()).isGreaterThanOrEqualTo(6); // At least 6 required fields
    }
    
    // Helper method
    
    private PaymentRequest createValidRequest() {
        return new PaymentRequest(
            "4532015112830366", // Valid Visa
            12,
            2025,
            "123",
            new BigDecimal("100.00"),
            "USD"
        );
    }
}
