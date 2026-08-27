CREATE INDEX IF NOT EXISTS idx_dsl_runs_status_started_at
    ON dsl_runs (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_dsl_runs_process_started_at
    ON dsl_runs (process_name, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_dsl_runs_execution_mode
    ON dsl_runs (execution_mode);