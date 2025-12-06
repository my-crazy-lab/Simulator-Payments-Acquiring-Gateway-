package com.paymentgateway.authorization.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/**
 * Validator implementation for payment amount validation.
 * Ensures amounts are within acceptable range and have proper precision.
 */
public class ValidAmountValidator implements ConstraintValidator<ValidAmount, BigDecimal> {
    
    private BigDecimal minValue;
    private BigDecimal maxValue;
    
    @Override
    public void initialize(ValidAmount constraintAnnotation) {
        this.minValue = new BigDecimal(constraintAnnotation.min());
        this.maxValue = new BigDecimal(constraintAnnotation.max());
    }
    
    @Override
    public boolean isValid(BigDecimal amount, ConstraintValidatorContext context) {
        if (amount == null) {
            return true; // Let @NotNull handle null validation
        }
        
        // Check if amount is within range
        if (amount.compareTo(minValue) < 0 || amount.compareTo(maxValue) > 0) {
            return false;
        }
        
        // Check decimal precision (max 2 decimal places for currency)
        if (amount.scale() > 2) {
            return false;
        }
        
        return true;
    }
}
