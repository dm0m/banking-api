package io.github.dm0m.bankingapi.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final Clock clock;
    private final int maxFailures;
    private final Duration failureWindow;
    private final Duration lockout;

    public LoginAttemptService(LoginAttemptRepository loginAttemptRepository,
        Clock clock,
        @Value("${app.login.max-failures}") int maxFailures,
        @Value("${app.login.failure-window}") Duration failureWindow,
        @Value("${app.login.lockout}") Duration lockout) {
            this.loginAttemptRepository = loginAttemptRepository;
            this.clock = clock;
            this.maxFailures = maxFailures;
            this.failureWindow = failureWindow;
            this.lockout = lockout;
    }

    public Instant getLockedUntil(String email) {
        return loginAttemptRepository.findById(email).map(LoginAttempt::getLockedUntil).orElse(null);
    }

    @Transactional
    public void recordFailure(String email) {
        Instant now = clock.instant();
        loginAttemptRepository.recordFailure(email, now, now.minus(failureWindow), maxFailures, now.plus(lockout));
    }

    @Transactional
    public void clearCount(String email) {
        loginAttemptRepository.deleteByEmail(email);
    }
}
