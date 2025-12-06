package com.paymentgateway.authorization.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator implementation for Luhn checksum algorithm.
 * 
 * The Luhn algorithm:
 * 1. Starting from the rightmost digit (check digit), double every second digit
 * 2. If doubling results in a two-digit number, add the digits together
 * 3. Sum all the digits
 * 4. If the sum modulo 10 is 0, the number is valid
 */
public class LuhnCheckValidator implements ConstraintValidator<LuhnCheck, String> {
    
    @Override
    public boolean isValid(String cardNumber, ConstraintValidatorContext context) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return true; // Let @NotBlank handle null/empty validation
        }
        
        // Remove any non-digit characters
        String digits = cardNumber.replaceAll("\\D", "");
        
        if (digits.isEmpty()) {
            return false;
        }
        
        return passesLuhnCheck(digits);
    }
    
    /**
     * Performs the Luhn checksum validation.
     * 
     * @param cardNumber the card number to validate (digits only)
     * @return true if the card number passes the Luhn check, false otherwise
     */
    public static boolean passesLuhnCheck(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return false;
        }
        
        int sum = 0;
        boolean alternate = false;
        
        // Process digits from right to left
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            
            if (digit < 0 || digit > 9) {
                return false; // Invalid digit
            }
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = digit - 9; // Equivalent to adding the two digits
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (sum % 10 == 0);
    }
}
