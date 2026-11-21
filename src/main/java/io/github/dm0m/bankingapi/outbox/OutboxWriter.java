package io.github.dm0m.bankingapi.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Adds an event to the outbox as part of the caller's transaction, so the event commits or rolls back
 * together with the change it describes. MANDATORY: called without a transaction it throws instead of
 * committing the event on its own, which would bring back the dual-write problem the outbox exists to fix.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String messageKey, UUID eventId, Object event) {
        repository.save(new OutboxEvent(eventId, topic, messageKey, toJson(event), Instant.now(clock)));
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize " + event.getClass().getSimpleName(), e);
        }
    }
}
