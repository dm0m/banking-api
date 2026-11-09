package io.github.dm0m.bankingapi.mapper;

import org.springframework.stereotype.Component;

import io.github.dm0m.bankingapi.dto.TransferRequest;
import io.github.dm0m.bankingapi.dto.TransferResponse;
import io.github.dm0m.bankingapi.model.Account;
import io.github.dm0m.bankingapi.model.Transfer;

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
