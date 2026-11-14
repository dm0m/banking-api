package io.github.dm0m.bankingapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Completed transfer")
public record TransferResponse(
    @Schema(description = "Transfer id", example = "1")
    Long id,
    @Schema(description = "Debited account", example = "1")
    Long fromAccountId,
    @Schema(description = "Credited account", example = "2")
    Long toAccountId,
    @Schema(description = "Amount moved", example = "30.0000")
    BigDecimal amount,
    @Schema(description = "Currency of both accounts")
    AccountCurrency currency,
    @Schema(description = "When the transfer was made (UTC)", example = "2026-01-15T10:30:00Z")
    Instant createdAt
) {}
