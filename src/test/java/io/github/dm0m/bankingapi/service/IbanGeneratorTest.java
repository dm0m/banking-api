package io.github.dm0m.bankingapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.dm0m.bankingapi.exception.UnsupportedCountryException;

class IbanGeneratorTest {

    private final IbanGenerator generator = new IbanGenerator();

    // how a bank validates an IBAN: move the first 4 chars to the end, letters to numbers, mod 97 must be 1
    private static boolean passesMod97(String iban) {
        String rearranged = iban.substring(4) + IbanGenerator.lettersToDigits(iban.substring(0, 2)) + iban.substring(2, 4);
        return new BigInteger(rearranged).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    @Test
    void checkDigits_matchesPublishedExampleIban() {
        // DE89 3704 0044 0532 0130 00 is the standard example IBAN used in IBAN documentation
        assertThat(IbanGenerator.checkDigits("DE", "370400440532013000")).isEqualTo("89");
    }

    @ParameterizedTest
    @CsvSource({
        "FR, 27",
        "DE, 22",
        "ES, 24",
        "BE, 16"
    })
    void generate_usesCountryCodeAndLength(String countryCode, int expectedLength) {
        String iban = generator.generate(countryCode);

        assertThat(iban).startsWith(countryCode).hasSize(expectedLength);
        assertThat(iban.substring(2)).containsOnlyDigits();
        assertThat(passesMod97(iban)).isTrue();
    }

    @Test
    void generate_everySupportedCountry_passesMod97Validation() {
        // random output, so generate several per country
        for (String code : IbanGenerator.BBAN_LENGTHS.keySet()) {
            for (int i = 0; i < 20; i++) {
                assertThat(passesMod97(generator.generate(code))).as(code).isTrue();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"US", "GB", "NL", "JP"})
    void generate_unsupportedCountry_throws(String countryCode) {
        assertThatThrownBy(() -> generator.generate(countryCode))
                .isInstanceOf(UnsupportedCountryException.class);
    }
}
