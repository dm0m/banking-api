package io.github.dm0m.bankingapi.exception;

public class AlreadyActivatedException extends RuntimeException {
    public AlreadyActivatedException(Long customerId) {
        super("Customer " + customerId + " already has an active login");
    }
}
