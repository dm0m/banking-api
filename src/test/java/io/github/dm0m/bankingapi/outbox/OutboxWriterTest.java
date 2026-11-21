package io.github.dm0m.bankingapi.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    @Mock
    private OutboxEventRepository repository;
    private OutboxWriter writer;
    record SampleEvent(UUID eventId, String amount) {}

    @BeforeEach
    void setUp() {
        writer = new OutboxWriter(repository, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void append_savesAnUnsentRowWithTheEventAsJson() {
        UUID eventId = UUID.randomUUID();
        writer.append("transfer.completed", "42", eventId, new SampleEvent(eventId, "30.50"));
        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(saved.capture());
        OutboxEvent row = saved.getValue();
        assertThat(row.getEventId()).isEqualTo(eventId);
        assertThat(row.getTopic()).isEqualTo("transfer.completed");
        assertThat(row.getMessageKey()).isEqualTo("42");
        assertThat(row.getPayload()).isEqualTo("{\"eventId\":\"" + eventId + "\",\"amount\":\"30.50\"}");
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
        assertThat(row.isSent()).isFalse();
    }

    @Test
    void append_eventJacksonCannotWrite_failsWithoutSaving() {
        Object unserializable = new Object();
        assertThatThrownBy(() -> writer.append("transfer.completed", "42", UUID.randomUUID(), unserializable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot serialize");
        verifyNoInteractions(repository);
    }
}
