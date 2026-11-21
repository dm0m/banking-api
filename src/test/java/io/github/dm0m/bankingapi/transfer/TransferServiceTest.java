package io.github.dm0m.bankingapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.dm0m.bankingapi.account.Account;
import io.github.dm0m.bankingapi.account.AccountCurrency;
import io.github.dm0m.bankingapi.account.AccountNotFoundException;
import io.github.dm0m.bankingapi.account.AccountRepository;
import io.github.dm0m.bankingapi.account.AccountStatus;
import io.github.dm0m.bankingapi.account.InactiveAccountException;
import io.github.dm0m.bankingapi.account.InvalidAmountException;
import io.github.dm0m.bankingapi.outbox.OutboxWriter;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String TOPIC = "transfer.completed";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransferRepository transferRepository;
    private TransferService transferService;
    
    @Mock
    private OutboxWriter outboxWriter;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(accountRepository, new TransferMapper(), transferRepository, outboxWriter, TOPIC);
    }

    private Account existingAccount(Long id, String balance) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", id);
        account.setBalance(new BigDecimal(balance));
        account.setCurrency(AccountCurrency.EUR);
        account.setAccountStatus(AccountStatus.ACTIVE);
        return account;
    }

    private void lockReturns(Account... accounts) {
        for (Account account : accounts) {
            when(accountRepository.findForUpdateById(account.getId())).thenReturn(Optional.of(account));
        }
    }

    // the id (sequence) and createdAt (JPA auditing) are set on save; the outbox event needs both
    private void saveReturnsArgument() {
        AtomicLong ids = new AtomicLong(41); // first saved transfer gets id 42
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer transfer = invocation.getArgument(0);
            ReflectionTestUtils.setField(transfer, "id", ids.incrementAndGet());
            ReflectionTestUtils.setField(transfer, "createdAt", Instant.parse("2026-11-18T10:00:00Z"));
            return transfer;
        });
    }

    private TransferRequest request(Long from, Long to, String amount) {
        return new TransferRequest(from, to, new BigDecimal(amount));
    }

    @Test
    void transfer_sufficientBalance_movesAmountBetweenAccounts() {
        Account from = existingAccount(1L, "100.0000");
        Account to = existingAccount(2L, "50.0000");
        lockReturns(from, to);
        saveReturnsArgument();
        TransferResponse response = transferService.transfer(request(1L, 2L, "30"));
        assertThat(from.getBalance()).isEqualByComparingTo("70");
        assertThat(to.getBalance()).isEqualByComparingTo("80");
        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(saved.capture());
        assertThat(saved.getValue().getFromAccount()).isSameAs(from);
        assertThat(saved.getValue().getToAccount()).isSameAs(to);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("30");
        assertThat(saved.getValue().getCurrency()).isEqualTo(AccountCurrency.EUR);
        assertThat(response.fromAccountId()).isEqualTo(1L);
        assertThat(response.toAccountId()).isEqualTo(2L);
        assertThat(response.amount()).isEqualByComparingTo("30");
        assertThat(response.currency()).isEqualTo(AccountCurrency.EUR);
    }

    @Test
    void transfer_writesOneTransferCompletedEventToTheOutbox() {
        Account from = existingAccount(2L, "100.0000");
        Account to = existingAccount(1L, "50.0000");
        lockReturns(from, to);
        saveReturnsArgument();

        transferService.transfer(new TransferRequest(2L, 1L, new BigDecimal("3E+1")));

        ArgumentCaptor<UUID> eventId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Object> written = ArgumentCaptor.forClass(Object.class);
        // keyed by transfer id, on the configured topic
        verify(outboxWriter).append(eq(TOPIC), eq("42"), eventId.capture(), written.capture());
        assertThat(written.getValue()).isInstanceOfSatisfying(TransferCompletedEvent.class, event -> {
            assertThat(event.eventId()).isNotNull().isEqualTo(eventId.getValue());
            assertThat(event.transferId()).isEqualTo(42L);
            assertThat(event.fromAccountId()).isEqualTo(2L);
            assertThat(event.toAccountId()).isEqualTo(1L);
            // the stored scale, not the request's notation
            assertThat(event.amount()).isEqualTo("30.0000");
            assertThat(event.currency()).isEqualTo(AccountCurrency.EUR);
            assertThat(event.occurredAt()).isEqualTo("2026-11-18T10:00:00Z");
        });
    }

    @Test
    void transfer_eachTransferGetsItsOwnEventId() {
        lockReturns(existingAccount(1L, "100.0000"), existingAccount(2L, "100.0000"));
        saveReturnsArgument();
        transferService.transfer(request(1L, 2L, "10"));
        transferService.transfer(request(1L, 2L, "10"));
        ArgumentCaptor<UUID> eventIds = ArgumentCaptor.forClass(UUID.class);
        verify(outboxWriter, times(2)).append(eq(TOPIC), any(), eventIds.capture(), any());
        assertThat(eventIds.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void transfer_fromHigherIdToLowerId_debitsSourceAndCreditsTarget() {
        // the accounts are locked in reverse order here, the roles must not swap
        Account from = existingAccount(2L, "100.0000");
        Account to = existingAccount(1L, "50.0000");
        lockReturns(from, to);
        saveReturnsArgument();
        transferService.transfer(request(2L, 1L, "30"));
        assertThat(from.getBalance()).isEqualByComparingTo("70");
        assertThat(to.getBalance()).isEqualByComparingTo("80");
    }

    @Test
    void transfer_amountEqualsBalance_leavesSourceAtZero() {
        Account from = existingAccount(1L, "30.0000");
        Account to = existingAccount(2L, "0.0000");
        lockReturns(from, to);
        saveReturnsArgument();
        transferService.transfer(request(1L, 2L, "30"));
        assertThat(from.getBalance()).isEqualByComparingTo("0");
        assertThat(to.getBalance()).isEqualByComparingTo("30");
    }

    @Test
    void transfer_locksLowerAccountIdFirst() {
        Account low = existingAccount(1L, "100.0000");
        Account high = existingAccount(2L, "100.0000");
        lockReturns(low, high);
        saveReturnsArgument();
        // transfer from the higher id: the lower id must still be locked first
        transferService.transfer(request(2L, 1L, "10"));
        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findForUpdateById(1L);
        order.verify(accountRepository).findForUpdateById(2L);
    }

    @Test
    void transfer_zeroAmount_isRejected() {
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "0")))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository, transferRepository, outboxWriter);
    }

    @Test
    void transfer_negativeAmount_isRejected() {
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "-10")))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository, transferRepository, outboxWriter);
    }

    @Test
    void transfer_sameAccount_isRejected() {
        assertThatThrownBy(() -> transferService.transfer(request(1L, 1L, "10")))
                .isInstanceOf(AccountsNotDistinctException.class);
        verifyNoInteractions(accountRepository, transferRepository, outboxWriter);
    }

    @Test
    void transfer_unknownSourceAccount_throwsAccountNotFound() {
        // source id is the lower one, so it is looked up first
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "10")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("1");
        verify(transferRepository, never()).save(any());
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void transfer_unknownTargetAccount_throwsAccountNotFound() {
        lockReturns(existingAccount(1L, "100.0000"));
        when(accountRepository.findForUpdateById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "10")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("2");
        verify(transferRepository, never()).save(any());
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void transfer_insufficientBalance_throwsLowBalanceAndChangesNothing() {
        Account from = existingAccount(1L, "10.0000");
        Account to = existingAccount(2L, "50.0000");
        lockReturns(from, to);
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "30")))
                .isInstanceOf(InsufficientFundsException.class);
        assertUnchanged(from, "10", to, "50");
    }

    @Test
    void transfer_differentCurrencies_isRejected() {
        Account from = existingAccount(1L, "100.0000");
        Account to = existingAccount(2L, "50.0000");
        to.setCurrency(AccountCurrency.USD);
        lockReturns(from, to);
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "30")))
                .isInstanceOf(CurrencyMismatchException.class);
        assertUnchanged(from, "100", to, "50");
    }

    @Test
    void transfer_inactiveSourceAccount_isRejected() {
        assertRejectedForStatus(AccountStatus.INACTIVE, true);
    }

    @Test
    void transfer_inactiveTargetAccount_isRejected() {
        assertRejectedForStatus(AccountStatus.INACTIVE, false);
    }

    @Test
    void transfer_suspendedSourceAccount_isRejected() {
        assertRejectedForStatus(AccountStatus.SUSPENDED, true);
    }

    @Test
    void transfer_suspendedTargetAccount_isRejected() {
        assertRejectedForStatus(AccountStatus.SUSPENDED, false);
    }

    private void assertRejectedForStatus(AccountStatus status, boolean onSource) {
        Account from = existingAccount(1L, "100.0000");
        Account to = existingAccount(2L, "50.0000");
        Account blocked = onSource ? from : to;
        blocked.setAccountStatus(status);
        lockReturns(from, to);
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "30")))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessageContaining("account " + blocked.getId());
        assertUnchanged(from, "100", to, "50");
    }

    private void assertUnchanged(Account from, String fromBalance, Account to, String toBalance) {
        assertThat(from.getBalance()).isEqualByComparingTo(fromBalance);
        assertThat(to.getBalance()).isEqualByComparingTo(toBalance);
        verify(transferRepository, never()).save(any());
        verifyNoInteractions(outboxWriter);
    }

    private Transfer savedTransfer(Long id, Account from, Account to, String amount) {
        Transfer transfer = new Transfer(from, to, new BigDecimal(amount), AccountCurrency.EUR);
        ReflectionTestUtils.setField(transfer, "id", id);
        return transfer;
    }

    @Test
    void getTransferById_existingId_returnsMappedTransfer() {
        Transfer transfer = savedTransfer(10L, existingAccount(1L, "0"), existingAccount(2L, "0"), "30");
        when(transferRepository.findById(10L)).thenReturn(Optional.of(transfer));

        TransferResponse response = transferService.getTransferById(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.fromAccountId()).isEqualTo(1L);
        assertThat(response.toAccountId()).isEqualTo(2L);
        assertThat(response.amount()).isEqualByComparingTo("30");
    }

    @Test
    void getTransferById_unknownId_throwsTransferNotFound() {
        when(transferRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.getTransferById(99L))
                .isInstanceOf(TransferNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getTransfersByAccountId_existingAccount_returnsMappedPageAndKeepsPagingInfo() {
        Account account = existingAccount(1L, "0");
        Account other = existingAccount(2L, "0");
        Pageable pageable = PageRequest.of(1, 2);
        // page 2 of 5 transfers: one sent, one received
        Page<Transfer> page = new PageImpl<>(
                List.of(savedTransfer(10L, account, other, "30"), savedTransfer(11L, other, account, "5")),
                pageable, 5);
        when(accountRepository.existsById(1L)).thenReturn(true);
        when(transferRepository.findByFromAccountIdOrToAccountId(1L, 1L, pageable)).thenReturn(page);

        Page<TransferResponse> result = transferService.getTransfersByAccountId(1L, pageable);

        assertThat(result.getContent()).extracting(TransferResponse::id).containsExactly(10L, 11L);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
    }

    @Test
    void getTransfersByAccountId_unknownAccount_throwsAccountNotFound() {
        when(accountRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> transferService.getTransfersByAccountId(99L, PageRequest.of(0, 20)))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(transferRepository);
    }
}
