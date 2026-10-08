-- T880: tamper-evident hash chain on dsl_audit.
--
-- Each row stores entry_hash = sha256(prev_hash || canonical(row fields)) and prev_hash points
-- to the previous row's entry_hash. The chain starts at the first post-migration row (legacy
-- rows keep both columns NULL — they are skipped by the verify walker). A unique index on
-- entry_hash accelerates the per-row chain lookup so verify remains O(n) without a full scan.
ALTER TABLE dsl_audit ADD COLUMN IF NOT EXISTS prev_hash VARCHAR(64);
ALTER TABLE dsl_audit ADD COLUMN IF NOT EXISTS entry_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_dsl_audit_entry_hash
    ON dsl_audit (entry_hash)
    WHERE entry_hash IS NOT NULL;
