package io.github.dm0m.bankingapi.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated string must be an ISO 3166-1 alpha-2 country code, such as {@code "FR"}.
 * Case and surrounding spaces are ignored ({@code " fr"} is valid).
 * {@code null} is valid; combine with {@code @NotBlank} to make it required.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Documented
@Constraint(validatedBy = CountryCodeValidator.class)
public @interface CountryCode {

    String message() default "must be an ISO 3166-1 alpha-2 country code, such as FR";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
