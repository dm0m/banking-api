package io.github.dm0m.bankingapi.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
class OutboxCleanupTest {

    @Mock
    private OutboxEventRepository repository;

    @Test
    void deletesEventsSentBeforeNowMinusTheRetention() {
        Instant now = Instant.parse("2026-11-18T03:00:00Z");
        OutboxCleanup cleanup = new OutboxCleanup(repository, Clock.fixed(now, ZoneOffset.UTC), Duration.ofDays(7));
        when(repository.deleteSentBefore(Instant.parse("2026-11-11T03:00:00Z"))).thenReturn(3);
        assertThat(cleanup.deleteOldSentEvents()).isEqualTo(3);
        verify(repository).deleteSentBefore(Instant.parse("2026-11-11T03:00:00Z"));
    }
}
