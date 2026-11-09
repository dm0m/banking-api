package io.github.dm0m.bankingapi.dto;

import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountType;
import jakarta.validation.constraints.NotNull;

public record OpenAccountRequest(
    @NotNull AccountType accountType,
    @NotNull AccountCurrency currency
) {}
