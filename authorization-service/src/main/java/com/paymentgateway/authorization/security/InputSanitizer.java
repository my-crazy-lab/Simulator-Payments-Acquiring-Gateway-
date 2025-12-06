package com.paymentgateway.authorization.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Input sanitization utility to prevent injection attacks.
 * Validates and sanitizes all input parameters.
 * 
 * Requirements: 24.2
 */
@Component
public class InputSanitizer {
    
    // Pattern for detecting potential SQL injection
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|ALTER|CREATE|TRUNCATE|EXEC|EXECUTE)\\b)|" +
        "(--|;|'|\"|\\*/|/\\*|xp_|sp_)|" +
        "(\\bOR\\b\\s+\\d+\\s*=\\s*\\d+)|" +  // OR 1=1 pattern
        "(\\bAND\\b\\s+\\d+\\s*=\\s*\\d+)",   // AND 1=1 pattern
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern for detecting potential XSS attacks
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(<script[^>]*>.*?</script>)|" +
        "(<[^>]*on\\w+\\s*=)|" +
        "(javascript:)|" +
        "(vbscript:)|" +
        "(<iframe)|" +
        "(<object)|" +
        "(<embed)|" +
        "(<link)|" +
        "(<meta)|" +
        "(<style[^>]*>.*?</style>)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // Pattern for valid alphanumeric with common safe characters
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9\\s\\-_.,@#$%&*()+=:;!?/\\\\]+$"
    );
    
    // Pattern for valid merchant ID
    private static final Pattern MERCHANT_ID_PATTERN = Pattern.compile(
        "^[A-Z0-9_-]{1,50}$"
    );
    
    // Pattern for valid payment ID
    private static final Pattern PAYMENT_ID_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_-]{1,100}$"
    );
    
    // Pattern for valid currency code (ISO 4217)
    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
        "^[A-Z]{3}$"
    );
    
    // Pattern for valid card number (digits only, 13-19 chars)
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile(
        "^[0-9]{13,19}$"
    );
    
    /**
     * Sanitizes a general string input by removing potentially dangerous characters.
     * 
     * @param input The input string to sanitize
     * @return The sanitized string, or null if input is null
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        
        // Trim whitespace
        String sanitized = input.trim();
        
        // Remove null bytes
        sanitized = sanitized.replace("\0", "");
        
        // Encode HTML entities
        sanitized = encodeHtmlEntities(sanitized);
        
        return sanitized;
    }
    
    /**
     * Checks if the input contains potential SQL injection patterns.
     * 
     * @param input The input to check
     * @return true if SQL injection is detected, false otherwise
     */
    public boolean containsSqlInjection(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }
    
    /**
     * Checks if the input contains potential XSS patterns.
     * 
     * @param input The input to check
     * @return true if XSS is detected, false otherwise
     */
    public boolean containsXss(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return XSS_PATTERN.matcher(input).find();
    }
    
    /**
     * Validates and sanitizes input, throwing exception if malicious content detected.
     * 
     * @param input The input to validate
     * @param fieldName The name of the field for error messages
     * @return The sanitized input
     * @throws IllegalArgumentException if malicious content is detected
     */
    public String validateAndSanitize(String input, String fieldName) {
        if (input == null) {
            return null;
        }
        
        if (containsSqlInjection(input)) {
            throw new IllegalArgumentException(
                "Invalid input detected in field: " + fieldName);
        }
        
        if (containsXss(input)) {
            throw new IllegalArgumentException(
                "Invalid input detected in field: " + fieldName);
        }
        
        return sanitize(input);
    }
    
    /**
     * Validates a merchant ID format.
     * 
     * @param merchantId The merchant ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidMerchantId(String merchantId) {
        if (merchantId == null || merchantId.isEmpty()) {
            return false;
        }
        return MERCHANT_ID_PATTERN.matcher(merchantId).matches();
    }
    
    /**
     * Validates a payment ID format.
     * 
     * @param paymentId The payment ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidPaymentId(String paymentId) {
        if (paymentId == null || paymentId.isEmpty()) {
            return false;
        }
        return PAYMENT_ID_PATTERN.matcher(paymentId).matches();
    }
    
    /**
     * Validates a currency code format (ISO 4217).
     * 
     * @param currency The currency code to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidCurrency(String currency) {
        if (currency == null || currency.isEmpty()) {
            return false;
        }
        return CURRENCY_PATTERN.matcher(currency).matches();
    }
    
    /**
     * Validates a card number format (digits only, proper length).
     * 
     * @param cardNumber The card number to validate
     * @return true if valid format, false otherwise
     */
    public boolean isValidCardNumberFormat(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return false;
        }
        // Remove spaces and dashes
        String cleaned = cardNumber.replaceAll("[\\s-]", "");
        return CARD_NUMBER_PATTERN.matcher(cleaned).matches();
    }
    
    /**
     * Encodes HTML entities to prevent XSS.
     * 
     * @param input The input to encode
     * @return The encoded string
     */
    public String encodeHtmlEntities(String input) {
        if (input == null) {
            return null;
        }
        
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
    
    /**
     * Strips all HTML tags from input.
     * 
     * @param input The input to strip
     * @return The input with HTML tags removed
     */
    public String stripHtmlTags(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("<[^>]*>", "");
    }
    
    /**
     * Checks if a string is safe (contains only allowed characters).
     * 
     * @param input The input to check
     * @return true if safe, false otherwise
     */
    public boolean isSafeString(String input) {
        if (input == null || input.isEmpty()) {
            return true; // Empty is considered safe
        }
        return SAFE_STRING_PATTERN.matcher(input).matches();
    }
}
