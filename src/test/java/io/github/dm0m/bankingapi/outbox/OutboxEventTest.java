package io.github.dm0m.bankingapi.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final Instant T1 = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-18T12:00:05Z");
    private final OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "transfer.completed", "1", "{}", T1);

    @Test
    void newEvent_isUnsentWithNoAttempts() {
        assertThat(event.isSent()).isFalse();
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getLastAttemptAt()).isNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void failureThenSuccess_countsBothAttemptsAndClearsTheError() {
        event.recordFailure(T1, "broker unreachable");
        event.markSent(T2);
        assertThat(event.isSent()).isTrue();
        assertThat(event.getSentAt()).isEqualTo(T2);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getLastAttemptAt()).isEqualTo(T2);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void recordFailure_truncatesLongErrors() {
        event.recordFailure(T1, "x".repeat(OutboxEvent.MAX_ERROR_LENGTH + 500));
        assertThat(event.getLastError()).hasSize(OutboxEvent.MAX_ERROR_LENGTH);
    }

    @Test
    void recordFailure_acceptsANullMessage() {
        event.recordFailure(T1, null);
        assertThat(event.getLastError()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void sentFailedOrReleased_eventIsNoLongerClaimed() {
        OutboxEvent failed = new OutboxEvent(UUID.randomUUID(), "transfer.completed", "1", "{}", T1);
        OutboxEvent released = new OutboxEvent(UUID.randomUUID(), "transfer.completed", "2", "{}", T1);
        event.claim(T2);
        failed.claim(T2);
        released.claim(T2);
        event.markSent(T1);
        failed.recordFailure(T1, "broker unreachable");
        released.release();
        assertThat(List.of(event, failed, released)).allMatch(e -> e.getClaimedUntil() == null);
        assertThat(released.getAttempts()).isZero();
    }
}
