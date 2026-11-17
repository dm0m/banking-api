package io.github.dm0m.bankingapi.customer;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Long id) {
        super("Customer " + id + " not found");
    }
}
