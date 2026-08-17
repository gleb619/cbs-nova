CREATE TABLE IF NOT EXISTS dsl_runs (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL UNIQUE,
    process_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_json TEXT,
    output_json TEXT,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    execution_mode VARCHAR(32)
);
