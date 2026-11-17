package io.github.dm0m.bankingapi.transfer;

public class AccountsNotDistinctException extends RuntimeException {
    public AccountsNotDistinctException() {
        super("transfer requires two distinct accounts");
    }
}
