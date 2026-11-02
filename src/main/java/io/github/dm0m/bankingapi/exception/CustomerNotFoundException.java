package io.github.dm0m.bankingapi.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Long id) {
        super("Customer " + id + " not found");
    }
}
