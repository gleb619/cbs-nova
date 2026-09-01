CREATE TABLE IF NOT EXISTS dsl_runs (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL UNIQUE,
    process_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_json TEXT,
    output_json TEXT,
    error_message TEXT,
    context_json TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    execution_mode VARCHAR(32),
    triggered_by VARCHAR(320),
    correlation_id VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS dsl_run_transactions (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL,
    transaction_name VARCHAR(255) NOT NULL,
    input_json TEXT,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dsl_run_transactions_run_id ON dsl_run_transactions(run_id);

CREATE INDEX IF NOT EXISTS idx_dsl_runs_status_started_at
    ON dsl_runs (status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_dsl_runs_process_started_at
    ON dsl_runs (process_name, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_dsl_runs_execution_mode
    ON dsl_runs (execution_mode);

CREATE INDEX IF NOT EXISTS idx_dsl_runs_correlation_id
    ON dsl_runs (correlation_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_dsl_runs_finished_at
    ON dsl_runs (finished_at)
    WHERE finished_at IS NOT NULL;
