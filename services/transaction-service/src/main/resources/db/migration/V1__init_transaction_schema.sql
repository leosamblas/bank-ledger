-- V1__init_transaction_schema.sql
-- Bank Ledger - transaction-service Initial Schema

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

-- Financial Transaction Table
CREATE TABLE IF NOT EXISTS financial_transaction (
    id                      UUID PRIMARY KEY,
    type                    VARCHAR(16) NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    status                  VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    source_account_id       UUID,
    destination_account_id  UUID,
    amount                  BIGINT NOT NULL CHECK (amount > 0),
    currency                CHAR(3) NOT NULL DEFAULT 'BRL',
    description             VARCHAR(140),
    requested_by            UUID NOT NULL,
    failure_code            VARCHAR(40),
    failure_reason          VARCHAR(255),
    correlation_id          UUID NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at            TIMESTAMP WITH TIME ZONE,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS ix_tx_source_created ON financial_transaction (source_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_tx_pending ON financial_transaction (created_at) WHERE status = 'PENDING';

-- Idempotency Key Table
CREATE TABLE IF NOT EXISTS idempotency_key (
    scope             UUID NOT NULL,
    idem_key          UUID NOT NULL,
    endpoint          VARCHAR(40) NOT NULL,
    request_hash      CHAR(64) NOT NULL,
    status            VARCHAR(16) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    transaction_id    UUID,
    response_status   INT,
    response_body     JSONB,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_idempotency_key PRIMARY KEY (scope, idem_key)
);

CREATE INDEX IF NOT EXISTS ix_idem_expires ON idempotency_key (expires_at);
