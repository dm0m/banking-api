package io.github.dm0m.bankingapi.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Transfer between two accounts of the same currency")
public record TransferRequest(

    @Schema(description = "Account to debit", example = "1")
    @NotNull Long fromAccountId,
    @Schema(description = "Account to credit; must differ from fromAccountId", example = "2")
    @NotNull Long toAccountId,
    @Schema(description = "Amount to move, positive, up to 4 decimals", example = "30.00")
    @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount
) {}
