-- V1__init_account_schema.sql
-- Bank Ledger - account-service Initial Schema

-- Account Number Generator Sequence
CREATE SEQUENCE IF NOT EXISTS account_number_seq START WITH 100000 INCREMENT BY 1;

-- Customer Table
CREATE TABLE IF NOT EXISTS customer (
    id                      UUID PRIMARY KEY,
    full_name               VARCHAR(150) NOT NULL,
    cpf                     VARCHAR(11) NOT NULL,
    email                   VARCHAR(255) NOT NULL,
    phone                   VARCHAR(20) NOT NULL,
    birth_date              DATE NOT NULL,
    password_hash           VARCHAR(255) NOT NULL,
    failed_login_attempts   INT NOT NULL DEFAULT 0,
    locked_until            TIMESTAMP WITH TIME ZONE,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_customer_cpf UNIQUE (cpf),
    CONSTRAINT uk_customer_email UNIQUE (email)
);

-- Account Table
CREATE TABLE IF NOT EXISTS account (
    id              UUID PRIMARY KEY,
    customer_id     UUID NOT NULL,
    branch          VARCHAR(4) NOT NULL DEFAULT '0001',
    account_number  VARCHAR(10) NOT NULL,
    check_digit     VARCHAR(1) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT uk_account_customer UNIQUE (customer_id),
    CONSTRAINT uk_account_branch_number UNIQUE (branch, account_number),
    CONSTRAINT chk_account_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'BLOCKED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS ix_account_customer_id ON account (customer_id);
CREATE INDEX IF NOT EXISTS ix_account_status ON account (status);

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
