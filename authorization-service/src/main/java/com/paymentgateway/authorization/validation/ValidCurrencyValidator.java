package com.paymentgateway.authorization.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator implementation for ISO 4217 currency codes.
 * Uses Java's Currency class to validate against standard currency codes.
 */
public class ValidCurrencyValidator implements ConstraintValidator<ValidCurrency, String> {
    
    private static final Set<String> VALID_CURRENCY_CODES = 
        Currency.getAvailableCurrencies()
            .stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toSet());
    
    @Override
    public boolean isValid(String currencyCode, ConstraintValidatorContext context) {
        if (currencyCode == null || currencyCode.isEmpty()) {
            return true; // Let @NotBlank handle null/empty validation
        }
        
        // Currency codes must be uppercase
        if (!currencyCode.equals(currencyCode.toUpperCase())) {
            return false;
        }
        
        return VALID_CURRENCY_CODES.contains(currencyCode);
    }
}
