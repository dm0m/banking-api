package io.github.dm0m.bankingapi.exception;


public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException() {
        super("amount must be positive");
    }
}
