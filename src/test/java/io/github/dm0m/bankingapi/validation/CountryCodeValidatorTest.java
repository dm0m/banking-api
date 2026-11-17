package io.github.dm0m.bankingapi.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CountryCodeValidatorTest {

    private final CountryCodeValidator validator = new CountryCodeValidator();

    @Test
    void nullValue_isValid() {
        boolean valid = validator.isValid(null, null);
        assertThat(valid).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FR", "DE", "US", "JP", "fr", "Fr", " FR "})
    void isoCode_isValid(String code) {
        boolean valid = validator.isValid(code, null);
        assertThat(valid).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FRA", "France", "XX", "F", "", "  "})
    void anythingElse_isInvalid(String value) {
        boolean valid = validator.isValid(value, null);
        assertThat(valid).isFalse();
    }
}
