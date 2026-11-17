-- Migration to add locked_until column for safe distributed outbox polling
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS ix_outbox_locked_until ON outbox_event (locked_until) WHERE published_at IS NULL;
