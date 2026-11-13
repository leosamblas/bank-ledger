-- V1__init_ledger_schema.sql
-- Bank Ledger - ledger-service Initial Schema

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

-- Canonical Transactional Inbox Table
CREATE TABLE IF NOT EXISTS inbox_event (
    event_id     UUID         NOT NULL,
    handler      VARCHAR(80)  NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_inbox_event PRIMARY KEY (event_id, handler)
);

-- Ledger Account Table (Balance in cents, strict balance >= 0 for customer accounts)
CREATE TABLE IF NOT EXISTS ledger_account (
    id             UUID         NOT NULL,
    code           VARCHAR(40)  NOT NULL,
    kind           VARCHAR(10)  NOT NULL,
    normal_balance VARCHAR(6)   NOT NULL,
    currency       CHAR(3)      NOT NULL DEFAULT 'BRL',
    balance        BIGINT       NOT NULL DEFAULT 0,
    last_sequence  BIGINT       NOT NULL DEFAULT 0,
    status         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_ledger_account PRIMARY KEY (id),
    CONSTRAINT uk_ledger_account_code UNIQUE (code),
    CONSTRAINT ck_la_kind CHECK (kind IN ('CUSTOMER', 'SYSTEM')),
    CONSTRAINT ck_la_normal_balance CHECK (normal_balance IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_la_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
    CONSTRAINT ck_la_balance CHECK (kind = 'SYSTEM' OR balance >= 0)
);

-- Journal Table (Transaction-level double-entry container)
CREATE TABLE IF NOT EXISTS journal (
    id             UUID         NOT NULL,
    transaction_id UUID         NOT NULL,
    type           VARCHAR(16)  NOT NULL,
    status         VARCHAR(10)  NOT NULL,
    reject_code    VARCHAR(40),
    reject_reason  VARCHAR(255),
    description    VARCHAR(140),
    total_amount   BIGINT       NOT NULL,
    currency       CHAR(3)      NOT NULL DEFAULT 'BRL',
    correlation_id UUID         NOT NULL,
    posted_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_journal PRIMARY KEY (id),
    CONSTRAINT uk_journal_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_journal_status CHECK (status IN ('POSTED', 'REJECTED'))
);

-- Entry Table (Individual legs of a double-entry journal)
CREATE TABLE IF NOT EXISTS entry (
    id                UUID         NOT NULL,
    journal_id        UUID         NOT NULL,
    ledger_account_id UUID         NOT NULL,
    direction         VARCHAR(6)   NOT NULL,
    amount            BIGINT       NOT NULL,
    balance_after     BIGINT       NOT NULL,
    sequence          BIGINT       NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_entry PRIMARY KEY (id),
    CONSTRAINT fk_entry_journal FOREIGN KEY (journal_id) REFERENCES journal (id),
    CONSTRAINT fk_entry_ledger_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_account (id),
    CONSTRAINT ck_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_entry_amount CHECK (amount > 0),
    CONSTRAINT uk_entry_account_seq UNIQUE (ledger_account_id, sequence)
);

CREATE INDEX IF NOT EXISTS ix_entry_account_created ON entry (ledger_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_entry_journal_id ON entry (journal_id);

-- Seed 16 System Cash Shards (SYS-CASH-00 to SYS-CASH-15) for lock contention dilution (ADR 0001)
INSERT INTO ledger_account (id, code, kind, normal_balance, status)
SELECT gen_random_uuid(), 'SYS-CASH-' || lpad(n::text, 2, '0'), 'SYSTEM', 'DEBIT', 'ACTIVE'
FROM generate_series(0, 15) AS n;
