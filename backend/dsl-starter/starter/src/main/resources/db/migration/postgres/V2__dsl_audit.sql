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
