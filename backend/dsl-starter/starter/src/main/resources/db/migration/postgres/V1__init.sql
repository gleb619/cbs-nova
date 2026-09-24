-- Initial schema for cbs-nova starter (PostgreSQL 16).
-- Merged from migrations V1-V11; apply in the order below (already ordered).

-- V1: core run tables
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
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dsl_run_transactions_run_id ON dsl_run_transactions(run_id);
CREATE INDEX IF NOT EXISTS idx_dsl_run_transactions_status ON dsl_run_transactions(status);

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

-- V2: append-only audit log
CREATE TABLE IF NOT EXISTS dsl_audit (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor VARCHAR(320) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target VARCHAR(512) NOT NULL,
    correlation_id VARCHAR(200),
    outcome VARCHAR(16) NOT NULL,
    details_json TEXT
);

-- Append-only audit log: rows are inserted by DslAuditRepository and never updated or deleted.
CREATE INDEX IF NOT EXISTS idx_dsl_audit_occurred_at ON dsl_audit (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_dsl_audit_action ON dsl_audit (action);

-- V3: append-only webhook delivery log
CREATE TABLE IF NOT EXISTS dsl_webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    subscription_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL,
    last_error VARCHAR(1000),
    duration_ms BIGINT
);

-- Append-only delivery log: rows are inserted by WebhookDeliveryRecordRepository and never updated or deleted.
CREATE INDEX IF NOT EXISTS idx_dsl_webhook_deliveries_subscription_occurred_at ON dsl_webhook_deliveries (subscription_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_dsl_webhook_deliveries_occurred_at ON dsl_webhook_deliveries (occurred_at DESC);

-- V4: append-only compile diagnostic log
CREATE TABLE IF NOT EXISTS dsl_compile_diagnostics (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(16) NOT NULL,
    definition VARCHAR(256) NOT NULL,
    file VARCHAR(512),
    line INT,
    col_number INT,
    severity VARCHAR(16) NOT NULL,
    code VARCHAR(64),
    message TEXT NOT NULL
);

-- Append-only compile diagnostic log: rows are inserted by CompileDiagnosticRecordRepository
-- and never updated or deleted.
CREATE INDEX IF NOT EXISTS idx_dsl_compile_diagnostics_occurred_at ON dsl_compile_diagnostics (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_dsl_compile_diagnostics_definition_occurred_at ON dsl_compile_diagnostics (definition, occurred_at DESC);

-- V5: author-defined definition test cases
CREATE TABLE IF NOT EXISTS dsl_definition_tests (
    id BIGSERIAL PRIMARY KEY,
    definition_name VARCHAR(200) NOT NULL,
    case_name VARCHAR(200) NOT NULL,
    input JSONB NOT NULL,
    expected_output JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Author-defined example inputs + expected outputs stored per definition; a case is uniquely
-- identified within a definition so authoring can replace the whole set in one transaction.
CREATE UNIQUE INDEX IF NOT EXISTS uq_dsl_definition_tests_name_case
    ON dsl_definition_tests (definition_name, case_name);
CREATE INDEX IF NOT EXISTS idx_dsl_definition_tests_definition_name
    ON dsl_definition_tests (definition_name);

-- V6: stored API keys (T410)
CREATE TABLE IF NOT EXISTS dsl_api_keys (
    id BIGSERIAL PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    key_prefix VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NULL
);

-- Stored API keys (T410). key_hash stores the SHA-256 hex digest of the plaintext key;
-- the plaintext is returned exactly once at creation and never persisted. Lookup is by
-- key_hash and is the only equality check, so a hash-collision pre-image attack is the
-- only avenue for forging a key (256-bit security). revoked_at IS NULL means active.
CREATE UNIQUE INDEX IF NOT EXISTS uq_dsl_api_keys_key_hash
    ON dsl_api_keys (key_hash);
CREATE INDEX IF NOT EXISTS idx_dsl_api_keys_revoked_at
    ON dsl_api_keys (revoked_at);

-- V7: append-only domain event store (T411)
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

-- V8: run attribution — tie every run to the definition version it executed (T492)
-- definition_hash is DESCRIPTOR identity: sha256 over the Jackson-serialized
-- DslDescriptor (taskQueue / version / timeouts), the same value the preview cache
-- keys on. It is NOT full logic identity — two functionally different definitions
-- with the same descriptor collide. A true content hash computed at publish/reload
-- time is a planned Epic 5 follow-up.
-- Nullable: historical rows and runs whose descriptor cannot be resolved stay NULL.
-- IF NOT EXISTS keeps the script idempotent: h2 test slices re-apply classpath
-- migrations per test method against a shared in-memory database.
ALTER TABLE dsl_runs ADD COLUMN IF NOT EXISTS definition_hash VARCHAR(64);

COMMENT ON COLUMN dsl_runs.definition_hash IS 'Descriptor-identity sha256 (64-char lowercase hex) of the DslDescriptor the run executed - NOT full logic identity; true content hash is a follow-up. NULL for historical rows and runs with an unresolvable definition.';

-- V9: notification rules engine (T565)
CREATE TABLE IF NOT EXISTS dsl_notification_rule (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64),
    aggregate_id_pattern VARCHAR(255),
    definition_pattern VARCHAR(255),
    status VARCHAR(64),
    actions JSONB NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    rate_class VARCHAR(64) NOT NULL DEFAULT 'default',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- T565: notification rules engine. The (event_type, enabled) index covers the engine's
-- hot lookup (all enabled rules for a published event type); finer filters
-- (aggregate glob, definition glob, status) are matched in the engine because globs
-- cannot be indexed cheaply. actions holds the sink list as JSONB.
CREATE INDEX IF NOT EXISTS idx_dsl_notification_rule_event_type_enabled
    ON dsl_notification_rule (event_type, enabled);

CREATE TABLE IF NOT EXISTS dsl_notification_rule_firing (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    sink VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    detail VARCHAR(1024),
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Append-only firing audit: one row per (matched rule, action) per published event.
-- event_id references dsl_events.id; synthetic /test runs do not write rows here.
CREATE INDEX IF NOT EXISTS idx_dsl_notification_rule_firing_rule_id
    ON dsl_notification_rule_firing (rule_id);
CREATE INDEX IF NOT EXISTS idx_dsl_notification_rule_firing_event_id
    ON dsl_notification_rule_firing (event_id);
CREATE INDEX IF NOT EXISTS idx_dsl_notification_rule_firing_created_at
    ON dsl_notification_rule_firing (created_at);

-- V10: track MQ delivery in the existing transactional outbox (T566)
ALTER TABLE dsl_events ADD COLUMN IF NOT EXISTS mq_published BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_dsl_events_mq_published
    ON dsl_events (mq_published) WHERE mq_published = false;

-- V11: change-request approval gate for DSL publish (T568)
CREATE TABLE IF NOT EXISTS dsl_change_request (
    id BIGSERIAL PRIMARY KEY,
    definition_name VARCHAR(255) NOT NULL,
    draft_content TEXT NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMP WITH TIME ZONE,
    comment VARCHAR(1024)
);

-- T568: change-request approval gate for DSL publish. The (definition_name, status) index
-- covers both the pending lookup used to supersede prior requests and the filtered list
-- endpoint. draft_content is a TEXT snapshot of the draft payload JSON (written via
-- SqlParameterValue OTHER in both dialects).
CREATE INDEX IF NOT EXISTS idx_dsl_change_request_definition_status
    ON dsl_change_request (definition_name, status);

CREATE INDEX IF NOT EXISTS idx_dsl_change_request_requested_at
    ON dsl_change_request (requested_at);
