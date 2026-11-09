package io.github.dm0m.bankingapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountStatus;
import io.github.dm0m.bankingapi.model.enums.AccountType;

public record AccountResponse(
    Long id,
    String accountNumber,
    Long customerId,
    BigDecimal balance,
    AccountType accountType,
    AccountCurrency currency,
    AccountStatus accountStatus,
    Instant createdAt,
    Instant updatedAt
) {}
