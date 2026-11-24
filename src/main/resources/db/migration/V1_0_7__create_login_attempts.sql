CREATE TABLE login_attempts (
    email VARCHAR(255) PRIMARY KEY,
    failed_count INTEGER NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    CONSTRAINT chk_login_attempts_failed_count CHECK (failed_count >= 0)
);

CREATE INDEX idx_login_attempts_window_started_at ON login_attempts (window_started_at);
