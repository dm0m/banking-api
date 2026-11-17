package io.github.dm0m.bankingapi.account;

import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public Account toEntity(OpenAccountRequest request) {
        // customer, account number, balance and status are decided by the service, not the client
        Account account = new Account();
        account.setAccountType(request.accountType());
        account.setCurrency(request.currency());
        return account;
    }

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getAccountNumber(),
            account.getCustomer().getId(),
            account.getBalance(),
            account.getAccountType(),
            account.getCurrency(),
            account.getAccountStatus(),
            account.getCreatedAt(),
            account.getUpdatedDate()
        );
    }
}
