CREATE SEQUENCE customers_id_seq INCREMENT BY 50;
CREATE SEQUENCE accounts_id_seq INCREMENT BY 50;
CREATE SEQUENCE addresses_id_seq INCREMENT BY 50;

CREATE TABLE addresses (
    id BIGINT PRIMARY KEY,
    street_no VARCHAR(10) NOT NULL,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    state VARCHAR(255) NOT NULL,
    zip_code VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    phone VARCHAR(16) NOT NULL,
    email VARCHAR(255) NOT NULL,
    dob DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    address_id BIGINT NOT NULL,
    FOREIGN KEY(address_id) REFERENCES addresses(id),
    UNIQUE(address_id),
    UNIQUE(email)
);

CREATE TABLE accounts (
    id BIGINT PRIMARY KEY,
    account_number VARCHAR(255) NOT NULL,
    account_status VARCHAR(10) CHECK(account_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')) NOT NULL,
    account_type VARCHAR(10) CHECK(account_type in ('CHECKING','SAVING')) NOT NULL,
    currency VARCHAR(3) CHECK(currency IN ('EUR', 'USD', 'JPY')) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    customer_id BIGINT NOT NULL,
    FOREIGN KEY(customer_id) REFERENCES customers(id),
    UNIQUE(account_number)
);

CREATE INDEX idx_accounts_customer_id ON accounts(customer_id);