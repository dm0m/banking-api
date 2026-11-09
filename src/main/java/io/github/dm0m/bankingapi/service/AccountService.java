package io.github.dm0m.bankingapi.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.dm0m.bankingapi.dto.AccountResponse;
import io.github.dm0m.bankingapi.dto.DepositRequest;
import io.github.dm0m.bankingapi.dto.OpenAccountRequest;
import io.github.dm0m.bankingapi.exception.AccountNotClosableException;
import io.github.dm0m.bankingapi.exception.AccountNotFoundException;
import io.github.dm0m.bankingapi.exception.CustomerNotFoundException;
import io.github.dm0m.bankingapi.exception.InactiveAccountException;
import io.github.dm0m.bankingapi.exception.InvalidAmountException;
import io.github.dm0m.bankingapi.mapper.AccountMapper;
import io.github.dm0m.bankingapi.model.Account;
import io.github.dm0m.bankingapi.model.Customer;
import io.github.dm0m.bankingapi.model.enums.AccountStatus;
import io.github.dm0m.bankingapi.repository.AccountRepository;
import io.github.dm0m.bankingapi.repository.CustomerRepository;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AccountMapper accountMapper;
    private final IbanGenerator ibanGenerator;

    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 5;
    private static final BigDecimal ZERO_BALANCE = BigDecimal.ZERO.setScale(4);

    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository,
            AccountMapper accountMapper, IbanGenerator ibanGenerator) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.accountMapper = accountMapper;
        this.ibanGenerator = ibanGenerator;
    }

    @Transactional
    public AccountResponse open(Long customerId, OpenAccountRequest request) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        Account account = accountMapper.toEntity(request);
        account.setCustomer(customer);
        account.setAccountNumber(generateUniqueAccountNumber(customer.getAddress().getCountry()));
        account.setBalance(ZERO_BALANCE);
        account.setAccountStatus(AccountStatus.ACTIVE);
        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {
        return accountMapper.toResponse(findAccount(id));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByCustomerId(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }
        return accountRepository.findByCustomerId(customerId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Transactional
    public AccountResponse close(Long id) {
        Account account = findAccountForUpdate(id);
        if (account.getAccountStatus() == AccountStatus.INACTIVE) {
            throw new AccountNotClosableException(id, "it is already closed");
        }
        // compareTo, not equals: BigDecimal.equals treats 0 and 0.0000 as different
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) { 
            throw new AccountNotClosableException(id, "its balance is not zero");
        }
        account.setAccountStatus(AccountStatus.INACTIVE);
        // flush now rather than at commit so auditing sets updatedAt before the response is built
        accountRepository.flush();
        return accountMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse deposit(Long id, DepositRequest request) {
        BigDecimal amount = request.amount();
        if (amount.signum() <= 0) throw new InvalidAmountException();
        // lock row: a concurrent transfer or deposit on this account waits instead of losing this update
        Account account = findAccountForUpdate(id);
        if (account.getAccountStatus() != AccountStatus.ACTIVE) throw new InactiveAccountException(id);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.flush();
        return accountMapper.toResponse(account);
    }

    private Account findAccount(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private Account findAccountForUpdate(Long id) {
        return accountRepository.findForUpdateById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
 
    private String generateUniqueAccountNumber(String country) {
        for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            String iban = ibanGenerator.generate(country);
            if (!accountRepository.existsByAccountNumber(iban)) {
                return iban;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }
}
