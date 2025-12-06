package com.paymentgateway.authorization.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for validation and other errors.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handles validation errors from @Valid annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        response.put("error", Map.of(
            "code", "VALIDATION_ERROR",
            "message", "Request validation failed",
            "fields", errors
        ));
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handles generic validation exceptions.
     */
    @ExceptionHandler(jakarta.validation.ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            jakarta.validation.ValidationException ex) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", Map.of(
            "code", "VALIDATION_ERROR",
            "message", ex.getMessage()
        ));
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
