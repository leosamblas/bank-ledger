-- V3__add_limit_and_protocol_to_query.sql
-- Bank Ledger - query-service Migration V3: Add limit to account_balance and protocol/counterparty to statement_entry

ALTER TABLE account_balance
    ADD COLUMN IF NOT EXISTS daily_transfer_limit_cents BIGINT NOT NULL DEFAULT 5000000;

ALTER TABLE statement_entry
    ADD COLUMN IF NOT EXISTS protocol_number VARCHAR(30),
    ADD COLUMN IF NOT EXISTS counterparty_document VARCHAR(20);

CREATE INDEX IF NOT EXISTS ix_statement_protocol ON statement_entry (protocol_number);
