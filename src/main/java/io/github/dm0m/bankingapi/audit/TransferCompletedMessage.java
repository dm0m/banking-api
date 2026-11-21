package io.github.dm0m.bankingapi.audit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.dm0m.bankingapi.account.AccountCurrency;

/**
 * The audit side's view of a transfer.completed message: only the JSON contract, not the producer's class,
 * so this consumer could move to its own service. A missing field fails deserialization, which the error
 * handler treats as not retryable.
 */
public record TransferCompletedMessage(
    UUID eventId,
    Long transferId,
    Long fromAccountId,
    Long toAccountId,
    BigDecimal amount,
    AccountCurrency currency,
    Instant occurredAt
) {
    public TransferCompletedMessage {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(fromAccountId, "fromAccountId");
        Objects.requireNonNull(toAccountId, "toAccountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
