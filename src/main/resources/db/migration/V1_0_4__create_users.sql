CREATE SEQUENCE users_id_seq INCREMENT BY 50;

CREATE TABLE users(
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('USER','ADMIN')),
    password_hash VARCHAR(255),
    customer_id BIGINT,
    activation_code_hash VARCHAR(255),
    activation_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(customer_id),
    UNIQUE(email),
    FOREIGN KEY(customer_id) REFERENCES customers(id),
    CHECK ((role = 'USER') = (customer_id IS NOT NULL))
);
