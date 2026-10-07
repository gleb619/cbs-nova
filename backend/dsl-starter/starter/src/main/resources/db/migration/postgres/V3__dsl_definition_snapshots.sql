-- T875: definition source snapshots — resolves dsl_runs.content_hash back to the exact source
-- snapshot a run executed. One row per (definition name, content hash); re-capturing an unchanged
-- hash is a no-op (unique constraint), so reload/publish are idempotent.
CREATE TABLE IF NOT EXISTS dsl_definition_snapshots (
    id BIGSERIAL PRIMARY KEY,
    definition_name VARCHAR(255) NOT NULL,
    definition_version VARCHAR(255),
    content_hash VARCHAR(64) NOT NULL,
    source_text TEXT NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    captured_by VARCHAR(16) NOT NULL,
    CONSTRAINT uk_dsl_definition_snapshots_name_hash UNIQUE (definition_name, content_hash)
);

CREATE INDEX IF NOT EXISTS idx_dsl_definition_snapshots_name
    ON dsl_definition_snapshots (definition_name, captured_at DESC);
