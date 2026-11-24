package io.github.dm0m.bankingapi.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "failed_count")
    private int failedCount;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public String getEmail() {
        return email;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public Instant getWindowStartedAt() {
        return windowStartedAt;
    }
}
