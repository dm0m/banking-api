package io.github.dm0m.bankingapi.exception;

public class AccountNotClosableException extends RuntimeException {
    public AccountNotClosableException(Long id, String reason) {
        super("Account " + id + " cannot be closed: " + reason);
    }
}
