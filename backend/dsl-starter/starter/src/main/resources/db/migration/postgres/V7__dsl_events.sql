CREATE TABLE IF NOT EXISTS dsl_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(128) NULL,
    payload JSONB NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- T411: append-only domain event store (phase 1: writes + list). Indexes cover the four
-- filter dimensions used by GET /api/dsl/events plus the newest-first ordering used by the
-- same endpoint. No update/delete path exists in the repository layer.
CREATE INDEX IF NOT EXISTS idx_dsl_events_aggregate
    ON dsl_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_dsl_events_correlation_id
    ON dsl_events (correlation_id);
CREATE INDEX IF NOT EXISTS idx_dsl_events_event_type
    ON dsl_events (event_type);
CREATE INDEX IF NOT EXISTS idx_dsl_events_created_at
    ON dsl_events (created_at);