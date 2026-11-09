package io.github.dm0m.bankingapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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

import io.github.dm0m.bankingapi.dto.TransferRequest;
import io.github.dm0m.bankingapi.dto.TransferResponse;
import io.github.dm0m.bankingapi.exception.AccountNotFoundException;
import io.github.dm0m.bankingapi.exception.AccountsNotDistinctException;
import io.github.dm0m.bankingapi.exception.CurrencyMismatchException;
import io.github.dm0m.bankingapi.exception.InactiveAccountException;
import io.github.dm0m.bankingapi.exception.InsufficientFundsException;
import io.github.dm0m.bankingapi.exception.InvalidAmountException;
import io.github.dm0m.bankingapi.exception.TransferNotFoundException;
import io.github.dm0m.bankingapi.mapper.TransferMapper;
import io.github.dm0m.bankingapi.model.Account;
import io.github.dm0m.bankingapi.model.Transfer;
import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountStatus;
import io.github.dm0m.bankingapi.repository.AccountRepository;
import io.github.dm0m.bankingapi.repository.TransferRepository;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransferRepository transferRepository;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(accountRepository, new TransferMapper(), transferRepository);
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

    private void saveReturnsArgument() {
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        verifyNoInteractions(accountRepository, transferRepository);
    }

    @Test
    void transfer_negativeAmount_isRejected() {
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "-10")))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(accountRepository, transferRepository);
    }

    @Test
    void transfer_sameAccount_isRejected() {
        assertThatThrownBy(() -> transferService.transfer(request(1L, 1L, "10")))
                .isInstanceOf(AccountsNotDistinctException.class);
        verifyNoInteractions(accountRepository, transferRepository);
    }

    @Test
    void transfer_unknownSourceAccount_throwsAccountNotFound() {
        // source id is the lower one, so it is looked up first
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "10")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("1");
        verify(transferRepository, never()).save(any());
    }

    @Test
    void transfer_unknownTargetAccount_throwsAccountNotFound() {
        lockReturns(existingAccount(1L, "100.0000"));
        when(accountRepository.findForUpdateById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> transferService.transfer(request(1L, 2L, "10")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("2");
        verify(transferRepository, never()).save(any());
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
