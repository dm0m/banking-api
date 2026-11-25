package io.github.dm0m.bankingapi.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginAttemptCleanup {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptCleanup.class);
    private final LoginAttemptRepository repository;
    private final Clock clock;
    private final Duration failureWindow;

    public LoginAttemptCleanup(
            LoginAttemptRepository repository,
            Clock clock,
            @Value("${app.login.failure-window}") Duration failureWindow) {
        this.repository = repository;
        this.clock = clock;
        this.failureWindow = failureWindow;
    }

    @Scheduled(cron = "${app.login.cleanup-cron}")
    @Transactional
    public int deleteExpiredAttempts() {
        Instant now = clock.instant();
        int deleted = repository.deleteExpired(now.minus(failureWindow), now);
        if (deleted > 0) logger.info("deleted {} expired login attempts", deleted);
        return deleted;
    }
}
