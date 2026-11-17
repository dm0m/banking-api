package io.github.dm0m.bankingapi.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Deposit into an account")
public record DepositRequest(
    @Schema(description = "Amount to add, positive, up to 4 decimals", example = "100.00")
    @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount
) {}
