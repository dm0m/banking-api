package io.github.dm0m.bankingapi.transfer;

import org.springframework.stereotype.Component;

import io.github.dm0m.bankingapi.account.Account;

@Component
public class TransferMapper {

    public Transfer toEntity(TransferRequest request, Account fromAccount, Account toAccount) {
        return new Transfer(fromAccount, toAccount, request.amount(), fromAccount.getCurrency());
    }

    public TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
            transfer.getId(),
            transfer.getFromAccount().getId(),
            transfer.getToAccount().getId(),
            transfer.getAmount(),
            transfer.getCurrency(),
            transfer.getCreatedAt()
        );
    }
}
