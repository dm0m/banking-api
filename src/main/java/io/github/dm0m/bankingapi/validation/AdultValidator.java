package io.github.dm0m.bankingapi.validation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link Adult}. Uses the application {@link Clock} so "today" is UTC and fixable in tests.
 */
public class AdultValidator implements ConstraintValidator<Adult, LocalDate> {

    private final Clock clock;
    private int minAge;
    private int maxAge;

    public AdultValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void initialize(Adult annotation) {
        this.minAge = annotation.minAge();
        this.maxAge = annotation.maxAge();
    }

    @Override
    public boolean isValid(LocalDate dob, ConstraintValidatorContext context) {
        if (dob == null) return true;
        int age = Period.between(dob, LocalDate.now(clock)).getYears();
        return age >= minAge && age <= maxAge;
    }
}
