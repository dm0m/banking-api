package io.github.dm0m.bankingapi.transfer;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.dm0m.bankingapi.account.Account;
import io.github.dm0m.bankingapi.account.AccountNotFoundException;
import io.github.dm0m.bankingapi.account.AccountRepository;
import io.github.dm0m.bankingapi.account.AccountStatus;
import io.github.dm0m.bankingapi.account.InactiveAccountException;
import io.github.dm0m.bankingapi.account.InvalidAmountException;
import io.github.dm0m.bankingapi.outbox.OutboxWriter;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferMapper transferMapper;
    private final TransferRepository transferRepository;
    private final OutboxWriter outboxWriter;
    private final String transferCompletedTopic;

    public TransferService(
        AccountRepository accountRepository,
        TransferMapper transferMapper,
        TransferRepository transferRepository,
        OutboxWriter outboxWriter,
        @Value("${app.kafka.topics.transfer-completed}") String transferCompletedTopic) {
            this.accountRepository = accountRepository;
            this.transferMapper = transferMapper;
            this.transferRepository = transferRepository;
            this.outboxWriter = outboxWriter;
            this.transferCompletedTopic = transferCompletedTopic;
    }

    private record LockedAccounts(Account from, Account to) {}

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        BigDecimal amount = request.amount();
        if (amount.signum() <= 0) throw new InvalidAmountException(); 
        Long from = request.fromAccountId();
        Long to = request.toAccountId();
        if (from.equals(to)) throw new AccountsNotDistinctException();
        LockedAccounts accounts = lockInIdOrder(from, to);
        // locked accounts are managed: the new balances are written at commit, no save() needed
        Account fromAccount = accounts.from();
        Account toAccount = accounts.to();
        validateTransfer(fromAccount, toAccount, amount);
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        Transfer newTransfer = transferRepository.save(transferMapper.toEntity(request, fromAccount, toAccount));
        TransferCompletedEvent event = new TransferCompletedEvent(
            UUID.randomUUID(),
            newTransfer.getId(),
            from,
            to,
            newTransfer.getAmount().toPlainString(),
            fromAccount.getCurrency(),
            newTransfer.getCreatedAt().toString());
        outboxWriter.append(transferCompletedTopic, newTransfer.getId().toString(), event.eventId(), event);
        return transferMapper.toResponse(newTransfer);
    }
    
    @Transactional(readOnly = true)
    public TransferResponse getTransferById(Long transferId) {
        return transferMapper.toResponse(transferRepository.findById(transferId).orElseThrow(() -> new TransferNotFoundException(transferId)));
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> getTransfersByAccountId(Long accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) throw new AccountNotFoundException(accountId);
        return transferRepository.findByFromAccountIdOrToAccountId(accountId, accountId, pageable).map(transferMapper::toResponse);
    }

    private Account findAccountForUpdate(Long id) {
        return accountRepository.findForUpdateById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    // lock the lower id first to avoid concurrent A->B and B->A transfers deadlock
    private LockedAccounts lockInIdOrder(Long from, Long to) {
        if (from < to) {
            Account fromAccount = findAccountForUpdate(from);
            Account toAccount = findAccountForUpdate(to);
            return new LockedAccounts(fromAccount, toAccount);
        }
        Account toAccount = findAccountForUpdate(to);
        Account fromAccount = findAccountForUpdate(from);
        return new LockedAccounts(fromAccount, toAccount);
    }

    private void validateTransfer(Account fromAccount, Account toAccount, BigDecimal amount) {
        if (fromAccount.getAccountStatus() != AccountStatus.ACTIVE) throw new InactiveAccountException(fromAccount.getId());
        if (toAccount.getAccountStatus() != AccountStatus.ACTIVE) throw new InactiveAccountException(toAccount.getId());
        if (fromAccount.getCurrency() != toAccount.getCurrency()) throw new CurrencyMismatchException();
        if (fromAccount.getBalance().compareTo(amount) < 0) throw new InsufficientFundsException(fromAccount.getId());
    }    
}
