package com.paymentgateway.shared.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Structured logger with PCI DSS compliance for sensitive data redaction
 */
public class StructuredLogger {
    private final Logger logger;

    private StructuredLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    public static StructuredLogger getLogger(Class<?> clazz) {
        return new StructuredLogger(clazz);
    }

    public void info(String message, Map<String, String> context) {
        setContext(context);
        logger.info(message);
        clearContext();
    }

    public void warn(String message, Map<String, String> context) {
        setContext(context);
        logger.warn(message);
        clearContext();
    }

    public void error(String message, Throwable throwable, Map<String, String> context) {
        setContext(context);
        logger.error(message, throwable);
        clearContext();
    }

    public void debug(String message, Map<String, String> context) {
        setContext(context);
        logger.debug(message);
        clearContext();
    }

    private void setContext(Map<String, String> context) {
        if (context != null) {
            context.forEach((key, value) -> {
                // Redact sensitive fields
                if (isSensitiveField(key)) {
                    MDC.put(key, redact(value));
                } else {
                    MDC.put(key, value);
                }
            });
        }
    }

    private void clearContext() {
        MDC.clear();
    }

    private boolean isSensitiveField(String fieldName) {
        String lowerField = fieldName.toLowerCase();
        return lowerField.contains("pan") || 
               lowerField.contains("cvv") || 
               lowerField.contains("card") ||
               lowerField.contains("password") ||
               lowerField.contains("secret") ||
               lowerField.contains("token");
    }

    private String redact(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        // Show only last 4 characters
        return "****" + value.substring(value.length() - 4);
    }
}
