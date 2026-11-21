CREATE SEQUENCE outbox_events_id_seq INCREMENT BY 50;

CREATE TABLE outbox_events (
    id BIGINT PRIMARY KEY,
    event_id UUID NOT NULL,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    claimed_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_unsent ON outbox_events (id) WHERE sent_at IS NULL;
CREATE INDEX idx_outbox_sent_at ON outbox_events (sent_at) WHERE sent_at IS NOT NULL;
