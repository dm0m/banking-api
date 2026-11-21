package io.github.dm0m.bankingapi.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;

@SpringBootTest(properties = "app.outbox.publisher.enabled=false")
@Import(TestcontainersConfiguration.class)
class OutboxEventRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    @Autowired
    private OutboxEventRepository repository;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void emptyOutbox() {
        jdbcTemplate.update("DELETE FROM outbox_events");
    }

    @Test
    void savedEvent_roundTripsEveryColumnIncludingTheJsonPayload() {
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\": \"" + eventId + "\", \"amount\": \"30.50\"}";
        Long id = repository.save(new OutboxEvent(eventId, "transfer.completed", "42", payload, NOW)).getId();
        OutboxEvent loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.getEventId()).isEqualTo(eventId);
        assertThat(loaded.getTopic()).isEqualTo("transfer.completed");
        assertThat(loaded.getMessageKey()).isEqualTo("42");
        assertThat(jdbcTemplate.queryForObject("SELECT payload = ?::jsonb FROM outbox_events WHERE id = ?",
                Boolean.class, payload, id)).isTrue();
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(loaded.getSentAt()).isNull();
        assertThat(loaded.getAttempts()).isZero();
    }

    @Test
    void lockClaimableBatch_returnsOnlyUnsentEventsOldestFirstUpToTheLimit() {
        Long first = saveUnsent();
        Long second = saveUnsent();
        saveSent(NOW);
        Long third = saveUnsent();
        List<Long> batch = tx.execute(status -> ids(repository.lockClaimableBatch(NOW, 10)));
        List<Long> limited = tx.execute(status -> ids(repository.lockClaimableBatch(NOW, 2)));
        assertThat(batch).containsExactly(first, second, third);
        assertThat(limited).containsExactly(first, second);
    }

    @Test
    void lockClaimableBatch_secondPublisherSkipsRowsTheFirstHasLocked() throws Exception {
        Long first = saveUnsent();
        Long second = saveUnsent();
        Long third = saveUnsent();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<List<Long>> publisherA = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            List<Long> taken = ids(repository.lockClaimableBatch(NOW, 2));
            locked.countDown();
            awaitQuietly(release);
            return taken;
        }));
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            List<Long> takenByB = tx.execute(status -> ids(repository.lockClaimableBatch(NOW, 10)));
            assertThat(takenByB).containsExactly(third);
        } finally {
            release.countDown();
        }
        assertThat(publisherA.get(5, TimeUnit.SECONDS)).containsExactly(first, second);
    }

    @Test
    void lockClaimableBatch_skipsEventsWithALiveClaimUntilItExpires() {
        Long claimed = saveUnsent();
        Long free = saveUnsent();
        Instant until = NOW.plusSeconds(60);
        tx.executeWithoutResult(status -> repository.findById(claimed).orElseThrow().claim(until));
        List<Long> whileClaimed = tx.execute(status -> ids(repository.lockClaimableBatch(NOW, 10)));
        List<Long> afterExpiry = tx.execute(status -> ids(repository.lockClaimableBatch(until.plusSeconds(1), 10)));
        assertThat(whileClaimed).containsExactly(free);
        assertThat(afterExpiry).containsExactly(claimed, free);
    }

    @Test
    void markSent_removesTheEventFromTheNextBatch() {
        Long id = saveUnsent();
        tx.executeWithoutResult(status -> repository.lockClaimableBatch(NOW, 10).forEach(event -> event.markSent(NOW)));
        OutboxEvent sent = repository.findById(id).orElseThrow();
        assertThat(sent.getSentAt()).isEqualTo(NOW);
        assertThat(sent.getAttempts()).isEqualTo(1);
        List<OutboxEvent> nextBatch = tx.execute(status -> repository.lockClaimableBatch(NOW, 10));
        assertThat(nextBatch).isEmpty();
        assertThat(repository.countBySentAtIsNull()).isZero();
    }

    @Test
    void recordFailure_keepsTheEventUnsentForTheNextRun() {
        Long id = saveUnsent();
        tx.executeWithoutResult(status -> repository.lockClaimableBatch(NOW, 10)
                .forEach(event -> event.recordFailure(NOW, "broker unreachable")));
        OutboxEvent failed = repository.findById(id).orElseThrow();
        assertThat(failed.isSent()).isFalse();
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastAttemptAt()).isEqualTo(NOW);
        assertThat(failed.getLastError()).isEqualTo("broker unreachable");
        List<Long> nextBatch = tx.execute(status -> ids(repository.lockClaimableBatch(NOW, 10)));
        assertThat(nextBatch).containsExactly(id);
    }

    @Test
    void deleteSentBefore_deletesOnlyOldSentEvents() {
        Instant cutoff = NOW.minus(7, ChronoUnit.DAYS);
        Long oldSent = saveSent(cutoff.minusSeconds(1));
        Long recentSent = saveSent(cutoff.plusSeconds(1));
        Long unsent = saveUnsent();
        Integer deleted = tx.execute(status -> repository.deleteSentBefore(cutoff));
        assertThat(deleted).isEqualTo(1);
        assertThat(repository.existsById(oldSent)).isFalse();
        assertThat(repository.existsById(recentSent)).isTrue();
        assertThat(repository.existsById(unsent)).isTrue();
    }

    private Long saveUnsent() {
        return repository.save(event()).getId();
    }

    private Long saveSent(Instant sentAt) {
        OutboxEvent event = event();
        event.markSent(sentAt);
        return repository.save(event).getId();
    }

    private static OutboxEvent event() {
        UUID eventId = UUID.randomUUID();
        return new OutboxEvent(eventId, "transfer.completed", "1", "{\"eventId\": \"" + eventId + "\"}", NOW);
    }

    private static List<Long> ids(List<OutboxEvent> events) {
        return events.stream().map(OutboxEvent::getId).toList();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test never released the locks");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
