package io.github.dm0m.bankingapi.dto;

import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New account; it starts ACTIVE with a zero balance")
public record OpenAccountRequest(
    @Schema(description = "Account type")
    @NotNull AccountType accountType,
    @Schema(description = "Currency of the account; fixed once opened")
    @NotNull AccountCurrency currency
) {}
