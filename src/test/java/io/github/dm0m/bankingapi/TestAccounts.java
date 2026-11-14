package io.github.dm0m.bankingapi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import io.github.dm0m.bankingapi.dto.AddressRequest;
import io.github.dm0m.bankingapi.dto.CreateCustomerRequest;
import io.github.dm0m.bankingapi.dto.DepositRequest;
import io.github.dm0m.bankingapi.dto.OpenAccountRequest;
import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountType;
import io.github.dm0m.bankingapi.service.AccountService;
import io.github.dm0m.bankingapi.service.CustomerService;

// Test data for integration tests, created through the real services.
public final class TestAccounts {

    private TestAccounts() {
    }

    // a new customer (unique email) with one active EUR account, funded by a deposit
    public static Long openWithBalance(CustomerService customerService, AccountService accountService, String balance) {
        AddressRequest address = new AddressRequest("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "FR");
        Long customerId = customerService.create(new CreateCustomerRequest(
            "John",
            "Doe",
            "+33612345678",
            UUID.randomUUID() + "@example.com",
            address,
            LocalDate.of(1990, 5, 15))
        ).id();
        Long accountId = accountService.open(
            customerId,
            new OpenAccountRequest(AccountType.CHECKING, AccountCurrency.EUR)
        ).id();
        BigDecimal amount = new BigDecimal(balance);
        if (amount.signum() > 0) {
            accountService.deposit(accountId, new DepositRequest(amount));
        }
        return accountId;
    }
}
