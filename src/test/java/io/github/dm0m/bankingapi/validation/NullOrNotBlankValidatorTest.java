package io.github.dm0m.bankingapi.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NullOrNotBlankValidatorTest {

    private final NullOrNotBlankValidator validator = new NullOrNotBlankValidator();

    @Test
    void nullValue_isValid() {
        boolean valid = validator.isValid(null, null);
        assertThat(valid).isTrue();
    }

    @Test
    void text_isValid() {
        boolean valid = validator.isValid("John", null);
        assertThat(valid).isTrue();
    }

    @Test
    void textSurroundedByWhitespace_isValid() {
        boolean valid = validator.isValid("  John  ", null);
        assertThat(valid).isTrue();
    }

    @Test
    void emptyString_isInvalid() {
        boolean valid = validator.isValid("", null);
        assertThat(valid).isFalse();
    }

    @Test
    void spacesOnly_isInvalid() {
        boolean valid = validator.isValid("   ", null);
        assertThat(valid).isFalse();
    }

    @Test
    void tabsAndNewlinesOnly_isInvalid() {
        boolean valid = validator.isValid("\t\n", null);
        assertThat(valid).isFalse();
    }
}
