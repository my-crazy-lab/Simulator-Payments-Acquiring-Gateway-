package com.paymentgateway.authorization.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that a card expiry date is not in the past.
 * This annotation should be applied at the class level to validate
 * both month and year fields together.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidExpiryDateValidator.class)
@Documented
public @interface ValidExpiryDate {
    String message() default "Card has expired";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    String monthField() default "expiryMonth";
    String yearField() default "expiryYear";
}
