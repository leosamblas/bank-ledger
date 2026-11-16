-- Authentication and Credentials Schema
-- Manages customer credentials, Argon2id hashes, lockout state, and JWT claims metadata

CREATE TABLE IF NOT EXISTS auth_credentials (
    id UUID PRIMARY KEY,
    cpf VARCHAR(11) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER',
    customer_id UUID NOT NULL,
    account_id UUID,
    branch VARCHAR(10),
    account_number VARCHAR(20),
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMPTZ,
    full_name VARCHAR(150),
    email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_customer UNIQUE (customer_id)
);

CREATE INDEX idx_auth_credentials_account_id ON auth_credentials(account_id);
