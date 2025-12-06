package com.paymentgateway.authorization.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Field;
import java.time.YearMonth;

/**
 * Validator implementation for card expiry date validation.
 * Validates that the expiry date (month/year) is not in the past.
 */
public class ValidExpiryDateValidator implements ConstraintValidator<ValidExpiryDate, Object> {
    
    private String monthField;
    private String yearField;
    
    @Override
    public void initialize(ValidExpiryDate constraintAnnotation) {
        this.monthField = constraintAnnotation.monthField();
        this.yearField = constraintAnnotation.yearField();
    }
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let other validators handle null
        }
        
        try {
            Field monthFieldObj = value.getClass().getDeclaredField(monthField);
            Field yearFieldObj = value.getClass().getDeclaredField(yearField);
            
            monthFieldObj.setAccessible(true);
            yearFieldObj.setAccessible(true);
            
            Integer month = (Integer) monthFieldObj.get(value);
            Integer year = (Integer) yearFieldObj.get(value);
            
            if (month == null || year == null) {
                return true; // Let @NotNull handle null values
            }
            
            // Validate month range
            if (month < 1 || month > 12) {
                return false;
            }
            
            // Check if the expiry date is in the past
            YearMonth expiryDate = YearMonth.of(year, month);
            YearMonth currentDate = YearMonth.now();
            
            return !expiryDate.isBefore(currentDate);
            
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Error accessing expiry date fields", e);
        }
    }
}
