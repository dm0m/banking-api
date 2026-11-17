package io.github.dm0m.bankingapi.auth;

// one message for unknown email, wrong, expired or already used code: nothing to probe
public class InvalidActivationCodeException extends RuntimeException {
    public InvalidActivationCodeException() {
        super("Invalid or expired activation code");
    }
}
