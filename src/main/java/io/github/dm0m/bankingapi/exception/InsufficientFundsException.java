package io.github.dm0m.bankingapi.exception;


public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long id) {
        super("low balance on account " + id);
    }
}
