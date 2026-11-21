package io.github.dm0m.bankingapi.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;

// Two publishers (two app instances) on the real database. Row locks only last for the claim transaction;
// while events are being sent, only claimed_until keeps the second publisher away from them.
// Same context as OutboxEventRepositoryIntegrationTest: the scheduled publisher is off, these drive their own.
@SpringBootTest(properties = "app.outbox.publisher.enabled=false")
@Import(TestcontainersConfiguration.class)
class OutboxPublisherConcurrencyIntegrationTest {

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
    void eventsBeingSentByOnePublisher_areNotSentByAnother() throws Exception {
        for (int i = 0; i < 3; i++) {
            repository.save(new OutboxEvent(UUID.randomUUID(), "transfer.completed", "1", "{}", NOW));
        }
        CountDownLatch sending = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaA = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaB = mock(KafkaTemplate.class);
        // A's sends stay unconfirmed until the test lets them through
        CompletableFuture<SendResult<Object, Object>> confirmation = new CompletableFuture<>();
        when(kafkaA.send(any(), any(), any())).thenAnswer(invocation -> {
            sending.countDown();
            return confirmation;
        });
        OutboxPublisher publisherA = publisher(kafkaA);
        OutboxPublisher publisherB = publisher(kafkaB);

        CompletableFuture<Integer> sentByA = CompletableFuture.supplyAsync(publisherA::publishBatch);
        int sentByB;
        try {
            assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
            // A's claim transaction has committed, so no row is locked any more
            sentByB = publisherB.publishBatch();
        } finally {
            confirmation.complete(null);
        }

        assertThat(sentByB).isZero();
        verifyNoInteractions(kafkaB);
        assertThat(sentByA.get(5, TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE sent_at IS NOT NULL AND attempts = 1 AND claimed_until IS NULL",
                Long.class)).isEqualTo(3);
    }

    private OutboxPublisher publisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new OutboxPublisher(repository, kafkaTemplate, tx, Clock.fixed(NOW, ZoneOffset.UTC), 10,
                Duration.ofSeconds(10));
    }
}
