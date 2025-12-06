package com.paymentgateway.authorization.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that a card number passes the Luhn checksum algorithm.
 * The Luhn algorithm is used to validate credit card numbers.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LuhnCheckValidator.class)
@Documented
public @interface LuhnCheck {
    String message() default "Invalid card number (Luhn checksum failed)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
