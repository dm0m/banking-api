CREATE SEQUENCE transfer_audit_id_seq INCREMENT BY 50;

CREATE TABLE transfer_audit_logs (
    id BIGINT PRIMARY KEY,
    event_id UUID NOT NULL,
    transfer_id BIGINT NOT NULL,
    from_account_id BIGINT NOT NULL,
    to_account_id BIGINT NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) CHECK(currency IN ('EUR', 'USD', 'JPY')) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    audited_at TIMESTAMPTZ NOT NULL,
    kafka_topic VARCHAR(255) NOT NULL,
    kafka_partition INTEGER NOT NULL,
    kafka_offset BIGINT NOT NULL,
    raw_payload JSONB NOT NULL,

    CONSTRAINT uq_transfer_audit_event_id UNIQUE (event_id),
    CONSTRAINT uq_transfer_audit_kafka_coords UNIQUE (kafka_topic, kafka_partition, kafka_offset)
);

CREATE INDEX idx_transfer_audit_transfer_id ON transfer_audit_logs (transfer_id);
CREATE INDEX idx_transfer_audit_from_account ON transfer_audit_logs (from_account_id);
CREATE INDEX idx_transfer_audit_to_account ON transfer_audit_logs (to_account_id);
CREATE INDEX idx_transfer_audit_occurred_at ON transfer_audit_logs (occurred_at);