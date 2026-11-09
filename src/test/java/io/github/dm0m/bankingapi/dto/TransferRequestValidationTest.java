package io.github.dm0m.bankingapi.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// amount format rules live on the DTO (@Digits, @Positive), not in TransferService
class TransferRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<ConstraintViolation<TransferRequest>> validate(String amount) {
        return validator.validate(new TransferRequest(1L, 2L, amount == null ? null : new BigDecimal(amount)));
    }

    @Test
    void amountWithFourDecimals_isValid() {
        assertThat(validate("10.1234")).isEmpty();
    }

    @Test
    void amountWithMoreThanFourDecimals_isRejected() {
        assertThat(validate("10.12345"))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    void zeroAmount_isRejected() {
        assertThat(validate("0")).isNotEmpty();
    }

    @Test
    void missingAmount_isRejected() {
        assertThat(validate(null)).isNotEmpty();
    }
}
