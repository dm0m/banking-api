CREATE SEQUENCE transfers_id_seq INCREMENT BY 50;

CREATE TABLE transfers (
    id BIGINT PRIMARY KEY,
    amount NUMERIC(19, 4) CHECK(amount > 0) NOT NULL,
    currency VARCHAR(3) CHECK(currency IN ('EUR', 'USD', 'JPY')) NOT NULL,
    from_account_id BIGINT NOT NULL,
    to_account_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY(from_account_id) REFERENCES accounts(id),
    FOREIGN KEY(to_account_id) REFERENCES accounts(id),
    CHECK(from_account_id <> to_account_id)
);

CREATE INDEX idx_transfers_from_account_created_at ON transfers(from_account_id, created_at);
CREATE INDEX idx_transfers_to_account_created_at ON transfers(to_account_id, created_at);
