package io.github.dm0m.bankingapi.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;

@SpringBootTest(properties = "app.outbox.publisher.enabled=false")
@Import(TestcontainersConfiguration.class)
class OutboxCleanupIntegrationTest {

    @Autowired
    private OutboxCleanup cleanup;
    @Autowired
    private OutboxEventRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Value("${app.outbox.retention}")
    private Duration retention;

    @BeforeEach
    void emptyOutbox() {
        jdbcTemplate.update("DELETE FROM outbox_events");
    }

    @Test
    void deletesSentEventsOlderThanTheRetention_andNothingElse() {
        Instant now = Instant.now();
        Long old = save(now.minus(retention).minus(Duration.ofHours(1)));
        Long recent = save(now.minus(retention).plus(Duration.ofHours(1)));
        Long unsent = save(null);
        int deleted = cleanup.deleteOldSentEvents();
        assertThat(deleted).isEqualTo(1);
        assertThat(repository.existsById(old)).isFalse();
        assertThat(repository.existsById(recent)).isTrue();
        assertThat(repository.existsById(unsent)).isTrue();
    }

    private Long save(Instant sentAt) {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "transfer.completed", "1", "{}", Instant.now());
        if (sentAt != null) {
            event.markSent(sentAt);
        }
        return repository.save(event).getId();
    }
}
