package io.github.dm0m.bankingapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginAttemptCleanupTest {

    @Mock
    private LoginAttemptRepository repository;

    @Test
    void deletesAttemptsWhoseWindowIsOver() {
        Instant now = Instant.parse("2026-11-18T03:00:00Z");
        LoginAttemptCleanup cleanup = new LoginAttemptCleanup(
                repository, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15));
        when(repository.deleteExpired(Instant.parse("2026-11-18T02:45:00Z"), now)).thenReturn(3);
        assertThat(cleanup.deleteExpiredAttempts()).isEqualTo(3);
    }
}
