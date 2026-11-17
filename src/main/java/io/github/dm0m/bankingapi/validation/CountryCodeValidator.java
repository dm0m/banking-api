package io.github.dm0m.bankingapi.validation;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates {@link CountryCode} against the JDK's list of ISO 3166-1 alpha-2 codes.
 */
public class CountryCodeValidator implements ConstraintValidator<CountryCode, String> {

    private static final Set<String> ISO_COUNTRIES = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // case and surrounding spaces are ignored; Address.setCountry stores the trimmed uppercase code
        return value == null || ISO_COUNTRIES.contains(value.trim().toUpperCase(Locale.ROOT));
    }
}
