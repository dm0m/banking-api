package io.github.dm0m.bankingapi.exception;

public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(Long id) {
        super("transfer " + id + " not found");
    }
}
