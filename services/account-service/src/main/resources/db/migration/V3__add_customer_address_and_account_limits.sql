-- V3__add_customer_address_and_account_limits.sql
-- Migration to add customer address and dynamic account transfer limits

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(9),
    ADD COLUMN IF NOT EXISTS street VARCHAR(150),
    ADD COLUMN IF NOT EXISTS number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS complement VARCHAR(100),
    ADD COLUMN IF NOT EXISTS neighborhood VARCHAR(100),
    ADD COLUMN IF NOT EXISTS city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS state VARCHAR(2);

ALTER TABLE account
    ADD COLUMN IF NOT EXISTS daily_transfer_limit_cents BIGINT NOT NULL DEFAULT 5000000;
