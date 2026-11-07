package io.github.dm0m.bankingapi.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.dm0m.bankingapi.exception.UnsupportedCountryException;

/**
 * Generates IBANs for the customer's country: country code + 2 ISO 13616 mod-97 check digits
 * + a random numeric BBAN of that country's length. National check digits inside the BBAN are
 * not computed. Uniqueness is the caller's responsibility.
 */
@Component
public class IbanGenerator {

    // BBAN length per supported country; only countries whose BBAN may be all digits
    static final Map<String, Integer> BBAN_LENGTHS = Map.of(
        "AT", 16,
        "BE", 12,
        "DE", 18,
        "ES", 20,
        "FI", 14,
        "FR", 23,
        "PT", 21
    );
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private final SecureRandom random = new SecureRandom();

    /** @param countryCode an ISO 3166-1 alpha-2 code, as validated by {@code @CountryCode} */
    public String generate(String countryCode) {
        Integer bbanLength = BBAN_LENGTHS.get(countryCode);
        if (bbanLength == null) {
            throw new UnsupportedCountryException(countryCode);
        }
        StringBuilder bban = new StringBuilder();
        for (int i = 0; i < bbanLength; i++) {
            bban.append(random.nextInt(10));
        }
        return countryCode + checkDigits(countryCode, bban.toString()) + bban;
    }

    // ISO 13616: move country code + "00" to the end, letters become numbers (A=10 .. Z=35),
    // and the check digits are 98 minus that number mod 97
    static String checkDigits(String countryCode, String bban) {
        String rearranged = bban + lettersToDigits(countryCode) + "00";
        int remainder = new BigInteger(rearranged).mod(NINETY_SEVEN).intValue();
        return String.format("%02d", 98 - remainder);
    }

    static String lettersToDigits(String letters) {
        StringBuilder digits = new StringBuilder();
        for (char c : letters.toCharArray()) {
            digits.append(Character.getNumericValue(c));
        }
        return digits.toString();
    }
}
