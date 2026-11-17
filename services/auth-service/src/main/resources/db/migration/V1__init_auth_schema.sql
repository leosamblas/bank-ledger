-- Authentication and Credentials Schema
-- Manages customer credentials, BCrypt hashes, lockout state, active tokens, and refresh tokens

CREATE TABLE IF NOT EXISTS auth_credentials (
    id UUID PRIMARY KEY,
    cpf VARCHAR(11) NOT NULL,
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
    active_token TEXT,
    token_expires_at TIMESTAMPTZ,
    refresh_token VARCHAR(128),
    refresh_token_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_customer UNIQUE (customer_id),
    CONSTRAINT uk_auth_cpf UNIQUE (cpf)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_auth_credentials_cpf ON auth_credentials(cpf);
CREATE INDEX IF NOT EXISTS idx_auth_credentials_account_id ON auth_credentials(account_id);
CREATE INDEX IF NOT EXISTS idx_auth_credentials_refresh_token ON auth_credentials(refresh_token);
