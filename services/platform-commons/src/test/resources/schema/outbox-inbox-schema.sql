CREATE TABLE IF NOT EXISTS outbox_event (
  id             uuid         NOT NULL,
  aggregate_type varchar(40)  NOT NULL,
  aggregate_id   uuid         NOT NULL,
  event_type     varchar(80)  NOT NULL,
  event_version  int          NOT NULL DEFAULT 1,
  topic          varchar(120) NOT NULL,
  message_key    varchar(64)  NOT NULL,
  payload        jsonb        NOT NULL,
  headers        jsonb        NOT NULL DEFAULT '{}'::jsonb,
  created_at     timestamptz  NOT NULL DEFAULT now(),
  published_at   timestamptz,
  attempts       int          NOT NULL DEFAULT 0,
  CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS ix_outbox_pending ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS inbox_event (
  event_id     uuid         NOT NULL,
  handler      varchar(80)  NOT NULL,
  processed_at timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT pk_inbox_event PRIMARY KEY (event_id, handler)
);
