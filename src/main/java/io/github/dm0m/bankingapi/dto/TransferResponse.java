package io.github.dm0m.bankingapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.dm0m.bankingapi.model.enums.AccountCurrency;

public record TransferResponse(
    Long id,
    Long fromAccountId,
    Long toAccountId,
    BigDecimal amount,
    AccountCurrency currency,
    Instant createdAt
) {}
