package io.github.dm0m.bankingapi.transfer;

import java.util.UUID;

import io.github.dm0m.bankingapi.account.AccountCurrency;

public record TransferCompletedEvent(
    UUID eventId,
    Long transferId,
    Long fromAccountId,
    Long toAccountId,
    String amount,
    AccountCurrency currency,
    String occurredAt) {

    static TransferCompletedEvent of(Transfer transfer) {
        return new TransferCompletedEvent(
            UUID.randomUUID(),
            transfer.getId(),
            transfer.getFromAccount().getId(),
            transfer.getToAccount().getId(),
            transfer.getAmount().toPlainString(),
            transfer.getFromAccount().getCurrency(),
            transfer.getCreatedAt().toString());
    }
}
