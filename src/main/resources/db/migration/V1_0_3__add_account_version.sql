-- optimistic locking: Hibernate increments version on every update of an account
ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
