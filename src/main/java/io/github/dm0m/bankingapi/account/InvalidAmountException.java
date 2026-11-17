package io.github.dm0m.bankingapi.account;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException() {
        super("amount must be positive");
    }
}
