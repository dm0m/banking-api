package io.github.dm0m.bankingapi.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated string may be {@code null} (field not sent), but if present it must contain
 * a non-whitespace character. Meant for optional fields in PATCH requests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Documented
@Constraint(validatedBy = NullOrNotBlankValidator.class)
public @interface NullOrNotBlank {

    String message() default "must not be blank";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
