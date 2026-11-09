package io.github.dm0m.bankingapi.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("Account " + id + " not found");
    }
}
