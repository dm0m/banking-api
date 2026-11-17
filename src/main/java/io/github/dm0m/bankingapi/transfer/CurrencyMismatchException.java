package io.github.dm0m.bankingapi.transfer;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException() {
        super("accounts must have the same currency");
    }
}
