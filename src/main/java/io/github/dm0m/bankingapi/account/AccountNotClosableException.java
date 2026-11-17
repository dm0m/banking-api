package io.github.dm0m.bankingapi.account;

public class AccountNotClosableException extends RuntimeException {
    public AccountNotClosableException(Long id, String reason) {
        super("Account " + id + " cannot be closed: " + reason);
    }
}
