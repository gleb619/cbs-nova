CREATE INDEX IF NOT EXISTS idx_dsl_runs_finished_at
    ON dsl_runs (finished_at)
    ${partial_where};
