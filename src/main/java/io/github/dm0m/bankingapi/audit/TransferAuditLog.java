package io.github.dm0m.bankingapi.audit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.dm0m.bankingapi.account.AccountCurrency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity 
@Table(name = "transfer_audit_logs")
public class TransferAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transfer_audit_id_seq")
    @SequenceGenerator(name = "transfer_audit_id_seq", sequenceName = "transfer_audit_id_seq", allocationSize = 50)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "transfer_id", updatable = false, nullable = false)
    private Long transferId;

    @Column(name = "from_account_id", updatable = false, nullable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", updatable = false, nullable = false)
    private Long toAccountId;

    @Column(name = "amount", precision = 19, scale = 4, updatable = false, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, updatable = false, nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountCurrency currency;

    @Column(name = "occurred_at", updatable = false, nullable = false)
    private Instant occurredAt;

    @Column(name = "audited_at", updatable = false, nullable = false)
    private Instant auditedAt;

    @Column(name = "kafka_topic", updatable = false, nullable = false)
    private String kafkaTopic;

    @Column(name = "kafka_partition", updatable = false, nullable = false)
    private Integer kafkaPartition;

    @Column(name = "kafka_offset", updatable = false, nullable = false)
    private Long kafkaOffset;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", updatable = false, nullable = false)
    private String rawPayload;

    protected TransferAuditLog() {
    }

    public TransferAuditLog(UUID eventId, Long transferId, Long fromAccountId, Long toAccountId, BigDecimal amount,
            AccountCurrency currency, Instant occurredAt, Instant auditedAt, String kafkaTopic, Integer kafkaPartition,
            Long kafkaOffset, String rawPayload) {
        this.eventId = eventId;
        this.transferId = transferId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.occurredAt = occurredAt;
        this.auditedAt = auditedAt;
        this.kafkaTopic = kafkaTopic;
        this.kafkaPartition = kafkaPartition;
        this.kafkaOffset = kafkaOffset;
        this.rawPayload = rawPayload;
    }

    public Long getId() {
        return id;
    }

}
