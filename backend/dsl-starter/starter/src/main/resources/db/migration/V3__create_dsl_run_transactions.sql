CREATE TABLE IF NOT EXISTS dsl_run_transactions (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL,
    transaction_name VARCHAR(255) NOT NULL,
    input_json TEXT,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dsl_run_transactions_run_id ON dsl_run_transactions(run_id);
