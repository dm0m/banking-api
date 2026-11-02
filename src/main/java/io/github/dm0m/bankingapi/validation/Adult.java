package io.github.dm0m.bankingapi.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The annotated date of birth must make the person between {@link #minAge()} and {@link #maxAge()}
 * years old today (both inclusive).
 * {@code null} is valid; combine with {@code @NotNull} when the field is required.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = AdultValidator.class)
public @interface Adult {

    String message() default "must be between {minAge} and {maxAge} years old";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    int minAge() default 18;
    int maxAge() default 120;
}
