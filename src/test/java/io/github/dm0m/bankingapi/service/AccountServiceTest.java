package io.github.dm0m.bankingapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.dm0m.bankingapi.dto.AccountResponse;
import io.github.dm0m.bankingapi.dto.OpenAccountRequest;
import io.github.dm0m.bankingapi.exception.AccountNotClosableException;
import io.github.dm0m.bankingapi.exception.AccountNotFoundException;
import io.github.dm0m.bankingapi.exception.CustomerNotFoundException;
import io.github.dm0m.bankingapi.mapper.AccountMapper;
import io.github.dm0m.bankingapi.model.Account;
import io.github.dm0m.bankingapi.model.Address;
import io.github.dm0m.bankingapi.model.Customer;
import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountStatus;
import io.github.dm0m.bankingapi.model.enums.AccountType;
import io.github.dm0m.bankingapi.repository.AccountRepository;
import io.github.dm0m.bankingapi.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private IbanGenerator ibanGenerator;
    private AccountService accountService;
    private final OpenAccountRequest openRequest = new OpenAccountRequest(AccountType.SAVING, AccountCurrency.EUR);

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, customerRepository, new AccountMapper(), ibanGenerator);
    }

    private Customer existingCustomer(Long id) {
        Address address = new Address();
        address.setCountry("FR");
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", id);
        customer.setAddress(address);
        return customer;
    }

    // an account as the repository would return it: already saved, so it has an id
    private Account existingAccount(Long id, String balance) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", id);
        account.setCustomer(existingCustomer(7L));
        account.setBalance(new BigDecimal(balance));
        account.setAccountStatus(AccountStatus.ACTIVE);
        return account;
    }

    // open

    @Test
    void open_existingCustomer_createsActiveAccountWithZeroBalance() {
        Customer customer = existingCustomer(7L);
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        // the generator must receive the country of the customer's address
        when(ibanGenerator.generate("FR")).thenReturn("FR-IBAN");
        when(accountRepository.existsByAccountNumber("FR-IBAN")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.open(7L, openRequest);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getCustomer()).isSameAs(customer);
        assertThat(saved.getValue().getAccountNumber()).isEqualTo("FR-IBAN");
        assertThat(saved.getValue().getBalance()).isEqualByComparingTo("0");
        assertThat(saved.getValue().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.customerId()).isEqualTo(7L);
        assertThat(response.accountType()).isEqualTo(AccountType.SAVING);
        assertThat(response.currency()).isEqualTo(AccountCurrency.EUR);
    }

    @Test
    void open_generatesUniqueAccountNumber() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(existingCustomer(7L)));
        when(ibanGenerator.generate("FR")).thenReturn("TAKEN", "FREE");
        when(accountRepository.existsByAccountNumber("TAKEN")).thenReturn(true);
        when(accountRepository.existsByAccountNumber("FREE")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.open(7L, openRequest);

        assertThat(response.accountNumber()).isEqualTo("FREE");
    }

    @Test
    void open_unknownCustomer_throwsCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.open(99L, openRequest))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void open_accountNumberAlwaysTaken_throwsAndDoesNotSave() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(existingCustomer(7L)));
        when(ibanGenerator.generate("FR")).thenReturn("TAKEN");
        when(accountRepository.existsByAccountNumber("TAKEN")).thenReturn(true);

        assertThatThrownBy(() -> accountService.open(7L, openRequest))
                .isInstanceOf(IllegalStateException.class);
        verify(ibanGenerator, times(5)).generate("FR");
        verify(accountRepository, never()).save(any());
    }

    // getAccountById

    @Test
    void getAccountById_existingAccount_returnsResponse() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(existingAccount(1L, "25.5000")));

        AccountResponse response = accountService.getAccountById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.customerId()).isEqualTo(7L);
        assertThat(response.balance()).isEqualByComparingTo("25.5");
        assertThat(response.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void getAccountById_unknownId_throwsAccountNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountById(99L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // getAccountsByCustomerId

    @Test
    void getAccountsByCustomerId_returnsAllAccountsOfCustomer() {
        when(customerRepository.existsById(7L)).thenReturn(true);
        when(accountRepository.findByCustomerId(7L))
                .thenReturn(List.of(existingAccount(1L, "10"), existingAccount(2L, "20")));

        List<AccountResponse> accounts = accountService.getAccountsByCustomerId(7L);

        assertThat(accounts).extracting(AccountResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void getAccountsByCustomerId_unknownCustomer_throwsCustomerNotFound() {
        when(customerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> accountService.getAccountsByCustomerId(99L))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(accountRepository, never()).findByCustomerId(any());
    }

    @Test
    void getAccountsByCustomerId_customerWithoutAccounts_returnsEmptyList() {
        when(customerRepository.existsById(7L)).thenReturn(true);
        when(accountRepository.findByCustomerId(7L)).thenReturn(List.of());

        assertThat(accountService.getAccountsByCustomerId(7L)).isEmpty();
    }

    // close

    @Test
    void close_zeroBalance_setsStatusInactive() {
        // scale 4 like a loaded balance: BigDecimal.equals(ZERO) would wrongly reject it
        Account account = existingAccount(1L, "0.0000");
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.close(1L);

        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(response.accountStatus()).isEqualTo(AccountStatus.INACTIVE);
        verify(accountRepository).flush();
    }

    @Test
    void close_nonZeroBalance_isRejected() {
        Account account = existingAccount(1L, "0.0001");
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.close(1L))
                .isInstanceOf(AccountNotClosableException.class)
                .hasMessageContaining("balance");
        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void close_alreadyClosed_isRejected() {
        Account account = existingAccount(1L, "0.0000");
        account.setAccountStatus(AccountStatus.INACTIVE);
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.close(1L))
                .isInstanceOf(AccountNotClosableException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void close_unknownId_throwsAccountNotFound() {
        when(accountRepository.findForUpdateById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.close(99L))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
