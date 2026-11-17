package io.github.dm0m.bankingapi.account;

public class InactiveAccountException extends RuntimeException {
    public InactiveAccountException(Long id) {
        super("account " + id + " is not active");
    }
    
}
