package io.github.dm0m.bankingapi.exception;


public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException() {
        super("accounts must have the same currency");
    }
}
