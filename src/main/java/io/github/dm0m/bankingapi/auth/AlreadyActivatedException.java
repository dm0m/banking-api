package io.github.dm0m.bankingapi.auth;

public class AlreadyActivatedException extends RuntimeException {
    public AlreadyActivatedException(Long customerId) {
        super("Customer " + customerId + " already has an active login");
    }
}
