package io.github.dm0m.bankingapi.auth;

// one message for every login failure, so the response never reveals whether the email exists
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
