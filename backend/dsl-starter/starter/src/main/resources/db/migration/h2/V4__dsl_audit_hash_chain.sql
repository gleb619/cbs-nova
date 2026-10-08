-- T880: tamper-evident hash chain on dsl_audit — H2 twin of the Postgres V4 migration.
-- Mirrors the column layout and unique index so the JDBC repository stays dialect-agnostic.
ALTER TABLE dsl_audit ADD COLUMN IF NOT EXISTS prev_hash VARCHAR(64);
ALTER TABLE dsl_audit ADD COLUMN IF NOT EXISTS entry_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_dsl_audit_entry_hash ON dsl_audit (entry_hash);
