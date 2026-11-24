package io.github.dm0m.bankingapi.auth;

import java.time.Instant;

public class LoginLockedException extends RuntimeException {

    private final Instant lockedUntil;

    public LoginLockedException(Instant lockedUntil) {
        super("Too many failed login attempts");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
