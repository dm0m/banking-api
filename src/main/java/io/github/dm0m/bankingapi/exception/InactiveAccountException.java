package io.github.dm0m.bankingapi.exception;

public class InactiveAccountException extends RuntimeException {
    public InactiveAccountException(Long id) {
        super("account " + id + " is not active");
    }
    
}
