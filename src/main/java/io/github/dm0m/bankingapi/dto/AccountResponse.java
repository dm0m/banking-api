package io.github.dm0m.bankingapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountStatus;
import io.github.dm0m.bankingapi.model.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bank account")
public record AccountResponse(
    @Schema(description = "Account id", example = "1")
    Long id,
    @Schema(description = "IBAN, generated for the customer's country", example = "FR7630006000011234567890189")
    String accountNumber,
    @Schema(description = "Owner of the account", example = "1")
    Long customerId,
    @Schema(description = "Current balance, up to 4 decimals", example = "250.0000")
    BigDecimal balance,
    @Schema(description = "Account type")
    AccountType accountType,
    @Schema(description = "Currency of the balance")
    AccountCurrency currency,
    @Schema(description = "Only ACTIVE accounts accept deposits and transfers")
    AccountStatus accountStatus,
    @Schema(description = "When the account was opened (UTC)", example = "2026-01-15T10:30:00Z")
    Instant createdAt,
    @Schema(description = "Last change (UTC)", example = "2026-01-15T10:30:00Z")
    Instant updatedAt
) {}
