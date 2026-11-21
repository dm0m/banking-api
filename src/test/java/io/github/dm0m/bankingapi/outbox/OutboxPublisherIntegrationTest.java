package io.github.dm0m.bankingapi.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.account.AccountCurrency;
import io.github.dm0m.bankingapi.transfer.TransferCompletedEvent;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxPublisherIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private OutboxWriter outboxWriter;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OutboxEventRepository repository;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${app.kafka.topics.transfer-completed}")
    private String topic;

    @Test
    void eventCommittedButNeverSent_isSentByThePublisherAndReachesTheConsumer() {
        UUID eventId = UUID.randomUUID();
        long transferId = 800_000_000L + (eventId.getLeastSignificantBits() & 0xFFFFFF);
        TransferCompletedEvent event = new TransferCompletedEvent(
                eventId, transferId, 900_000_001L, 900_000_002L, "12.50", AccountCurrency.EUR, "2026-11-18T10:00:00Z");
        tx.executeWithoutResult(status -> outboxWriter.append(topic, String.valueOf(transferId), eventId, event));
        await().atMost(TIMEOUT).until(() -> count(
                "SELECT COUNT(*) FROM transfer_audit_logs WHERE event_id = ?", eventId) == 1);
        await().atMost(TIMEOUT).until(() -> count(
                "SELECT COUNT(*) FROM outbox_events WHERE event_id = ? AND sent_at IS NOT NULL", eventId) == 1);
        assertThat(count("SELECT attempts FROM outbox_events WHERE event_id = ?", eventId)).isEqualTo(1);
    }

    @Test
    void eventClaimedByAPublisherThatDied_isSentOnceTheClaimExpires() throws Exception {
        OutboxEvent event = outboxEvent();
        event.claim(Instant.now().minusSeconds(1));
        repository.save(event);
        awaitDelivered(event.getEventId());
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_id = ? AND claimed_until IS NULL",
                event.getEventId())).isEqualTo(1);
    }

    @Test
    void eventThatFailedBefore_isRetriedAndSent_andItsErrorIsCleared() throws Exception {
        OutboxEvent event = outboxEvent();
        event.recordFailure(Instant.now().minusSeconds(30), "TimeoutException: broker unreachable");
        event.recordFailure(Instant.now().minusSeconds(10), "TimeoutException: broker unreachable");
        repository.save(event);
        awaitDelivered(event.getEventId());
        assertThat(count("SELECT attempts FROM outbox_events WHERE event_id = ?", event.getEventId())).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_id = ? AND last_error IS NULL",
                event.getEventId())).isEqualTo(1);
    }

    private OutboxEvent outboxEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        long transferId = 800_000_000L + (eventId.getLeastSignificantBits() & 0xFFFFFF);
        TransferCompletedEvent event = new TransferCompletedEvent(
                eventId, transferId, 900_000_001L, 900_000_002L, "7.00", AccountCurrency.EUR, "2026-11-18T10:00:00Z");
        return new OutboxEvent(eventId, topic, String.valueOf(transferId), objectMapper.writeValueAsString(event),
                Instant.now());
    }

    private void awaitDelivered(UUID eventId) {
        await().atMost(TIMEOUT).until(() -> count(
                "SELECT COUNT(*) FROM transfer_audit_logs WHERE event_id = ?", eventId) == 1);
        await().atMost(TIMEOUT).until(() -> count(
                "SELECT COUNT(*) FROM outbox_events WHERE event_id = ? AND sent_at IS NOT NULL", eventId) == 1);
    }

    private long count(String sql, UUID eventId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, eventId);
        return value != null ? value : 0;
    }
}
