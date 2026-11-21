package io.github.dm0m.bankingapi.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_events_id_seq")
    @SequenceGenerator(name = "outbox_events_id_seq", sequenceName = "outbox_events_id_seq", allocationSize = 50)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "topic", updatable = false, nullable = false)
    private String topic;

    @Column(name = "message_key", updatable = false, nullable = false)
    private String messageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", updatable = false, nullable = false)
    private String payload;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID eventId, String topic, String messageKey, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void claim(Instant until) {
        this.claimedUntil = until;
    }

    public void release() {
        this.claimedUntil = null;
    }

    public void markSent(Instant now) {
        this.sentAt = now;
        this.attempts++;
        this.lastAttemptAt = now;
        this.lastError = null;
        this.claimedUntil = null;
    }

    public void recordFailure(Instant now, String error) {
        this.attempts++;
        this.lastAttemptAt = now;
        this.lastError = error != null && error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        this.claimedUntil = null;
    }

    public boolean isSent() {
        return sentAt != null;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getClaimedUntil() {
        return claimedUntil;
    }

    public long getVersion() {
        return version;
    }
}
