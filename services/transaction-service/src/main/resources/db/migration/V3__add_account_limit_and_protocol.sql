-- V3__add_account_limit_and_protocol.sql
-- Dynamic transfer limits per account and canonical protocol number

CREATE TABLE IF NOT EXISTS account_limit (
    account_id                 UUID PRIMARY KEY,
    daily_transfer_limit_cents BIGINT NOT NULL DEFAULT 5000000,
    approved_by                VARCHAR(80) NOT NULL DEFAULT 'SYSTEM_DEFAULT',
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE financial_transaction
    ADD COLUMN IF NOT EXISTS protocol_number VARCHAR(30);

CREATE INDEX IF NOT EXISTS ix_tx_protocol ON financial_transaction (protocol_number);
CREATE INDEX IF NOT EXISTS ix_tx_daily_limit ON financial_transaction (source_account_id, type, status, created_at);
