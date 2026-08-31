ALTER TABLE dsl_runs ADD COLUMN correlation_id VARCHAR(200);
CREATE INDEX IF NOT EXISTS idx_dsl_runs_correlation_id ON dsl_runs (correlation_id, started_at DESC);
