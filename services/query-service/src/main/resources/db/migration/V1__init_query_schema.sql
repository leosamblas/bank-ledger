-- V1__init_query_schema.sql
-- Bank Ledger - query-service Initial Schema

-- Canonical Transactional Outbox Table
CREATE TABLE IF NOT EXISTS outbox_event (
    id             UUID         NOT NULL,
    aggregate_type VARCHAR(40)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(80)  NOT NULL,
    event_version  INT          NOT NULL DEFAULT 1,
    topic          VARCHAR(120) NOT NULL,
    message_key    VARCHAR(64)  NOT NULL,
    payload        JSONB        NOT NULL,
    headers        JSONB        NOT NULL DEFAULT '{}'::JSONB,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    published_at   TIMESTAMP WITH TIME ZONE,
    attempts       INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS ix_outbox_pending ON outbox_event (created_at) WHERE published_at IS NULL;

-- Canonical Transactional Inbox Table (for idempotent event handling)
CREATE TABLE IF NOT EXISTS inbox_event (
    event_id     UUID         NOT NULL,
    handler      VARCHAR(80)  NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_inbox_event PRIMARY KEY (event_id, handler)
);

-- Account Balance Projection Table (CQRS Read Model)
CREATE TABLE IF NOT EXISTS account_balance (
    account_id    UUID        NOT NULL,
    balance       BIGINT      NOT NULL DEFAULT 0,
    currency      CHAR(3)     NOT NULL DEFAULT 'BRL',
    last_sequence BIGINT      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_account_balance PRIMARY KEY (account_id)
);

-- Statement Entry Projection Table (CQRS Read Model)
CREATE TABLE IF NOT EXISTS statement_entry (
    id                           UUID         NOT NULL,
    account_id                   UUID         NOT NULL,
    sequence                     BIGINT       NOT NULL,
    journal_id                   UUID         NOT NULL,
    transaction_id               UUID         NOT NULL,
    category                     VARCHAR(20)  NOT NULL,
    type                         VARCHAR(20)  NOT NULL,
    direction                    VARCHAR(6)   NOT NULL,
    amount                       BIGINT       NOT NULL,
    balance_after                BIGINT       NOT NULL,
    counterparty_name            VARCHAR(120),
    counterparty_document_masked VARCHAR(20),
    counterparty_account         VARCHAR(40),
    description                  VARCHAR(140),
    idempotency_key              VARCHAR(64),
    failure_reason               VARCHAR(255),
    posted_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_statement_entry PRIMARY KEY (id),
    CONSTRAINT uk_statement_account_seq UNIQUE (account_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_statement_account_posted_at ON statement_entry (account_id, posted_at DESC);
CREATE INDEX IF NOT EXISTS idx_statement_account_seq ON statement_entry (account_id, sequence DESC);
