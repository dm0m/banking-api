package io.github.dm0m.bankingapi.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositRequest(
    @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount
) {}
