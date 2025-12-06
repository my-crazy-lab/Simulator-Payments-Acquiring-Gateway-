package com.paymentgateway.authorization.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that an amount is within acceptable range for payment processing.
 * Default range: 0.01 to 999,999,999.99
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidAmountValidator.class)
@Documented
public @interface ValidAmount {
    String message() default "Amount must be between {min} and {max}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    String min() default "0.01";
    String max() default "999999999.99";
}
