package io.github.dm0m.bankingapi.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AdultValidatorTest {

    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC);

    private static class Annotated {
        @Adult
        Object defaults;

        @Adult(minAge = 21, maxAge = 65)
        Object custom;
    }

    private static AdultValidator validator(Clock clock, String field) throws NoSuchFieldException {
        AdultValidator validator = new AdultValidator(clock);
        validator.initialize(Annotated.class.getDeclaredField(field).getAnnotation(Adult.class));
        return validator;
    }

    @Test
    void nullDateOfBirth_isValid() throws Exception {
        AdultValidator validator = validator(TODAY, "defaults");
        boolean valid = validator.isValid(null, null);
        assertThat(valid).isTrue();
    }

    @Test
    void exactlyMinAgeToday_isValid() throws Exception {
        AdultValidator validator = validator(TODAY, "defaults");
        LocalDate dob = LocalDate.of(2008, 11, 2);
        boolean valid = validator.isValid(dob, null);
        assertThat(valid).isTrue();
    }

    @Test
    void reachesMinAgeTomorrow_isInvalid() throws Exception {
        AdultValidator validator = validator(TODAY, "defaults");
        LocalDate dob = LocalDate.of(2008, 11, 03);
        boolean valid = validator.isValid(dob, null);
        assertThat(valid).isFalse();
    }

    @Test
    void exactlyMaxAgeToday_isValid() throws Exception {
        AdultValidator validator = validator(TODAY, "defaults");
        LocalDate dob = LocalDate.of(1906, 11, 2);
        boolean valid = validator.isValid(dob, null);
        assertThat(valid).isTrue();
    }

    @Test
    void olderThanMaxAge_isInvalid() throws Exception {
        AdultValidator validator = validator(TODAY, "defaults");
        LocalDate dob = LocalDate.of(1905, 11, 2);
        boolean valid = validator.isValid(dob, null);
        assertThat(valid).isFalse();
    }

    @Test
    void futureDateOfBirth_isInvalid() throws Exception {
        AdultValidator validator = validator(TODAY, "defaults");
        LocalDate dob = LocalDate.of(2027, 1, 1);
        boolean valid = validator.isValid(dob, null);
        assertThat(valid).isFalse();
    }

    @Test
    void bornOnLeapDay_isNotAdultOnFeb28() throws Exception {
        Clock feb28 = Clock.fixed(Instant.parse("2026-02-28T00:00:00Z"), ZoneOffset.UTC);
        AdultValidator validator = validator(feb28, "defaults");
        LocalDate dob = LocalDate.of(2008, 2, 29);
        boolean valid = validator.isValid(dob, null);
        assertThat(valid).isFalse();
    }

    @Test
    void bornOnLeapDay_isAdultOnMarch1() throws Exception {
        Clock march1 = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);
        AdultValidator validator = validator(march1, "defaults");
        LocalDate dob = LocalDate.of(2008, 2, 29);
        boolean valid = validator.isValid(dob, null);
        assertThat(valid).isTrue();
    }

    @Test
    void customMinAndMaxAge_areRespected() throws Exception {
        AdultValidator validator = validator(TODAY, "custom");
        assertThat(validator.isValid(LocalDate.of(2005, 11, 2), null)).isTrue();  // exactly 21
        assertThat(validator.isValid(LocalDate.of(2005, 11, 3), null)).isFalse(); // still 20
        assertThat(validator.isValid(LocalDate.of(1961, 11, 2), null)).isTrue();  // exactly 65
        assertThat(validator.isValid(LocalDate.of(1960, 11, 2), null)).isFalse(); // 66
    }
}
