-- T566: track MQ delivery in the existing transactional outbox.
ALTER TABLE dsl_events ADD COLUMN IF NOT EXISTS mq_published BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_dsl_events_mq_published
    ON dsl_events (mq_published) WHERE mq_published = false;
