-- T492: run attribution — tie every run to the definition version it executed.
-- definition_hash is DESCRIPTOR identity: sha256 over the Jackson-serialized
-- DslDescriptor (taskQueue / version / timeouts), the same value the preview cache
-- keys on. It is NOT full logic identity — two functionally different definitions
-- with the same descriptor collide. A true content hash computed at publish/reload
-- time is a planned Epic 5 follow-up.
-- Nullable: historical rows and runs whose descriptor cannot be resolved stay NULL.
-- IF NOT EXISTS keeps the script idempotent: h2 test slices re-apply classpath
-- migrations per test method against a shared in-memory database.
ALTER TABLE dsl_runs ADD COLUMN IF NOT EXISTS definition_hash VARCHAR(64);

COMMENT ON COLUMN dsl_runs.definition_hash IS
    'Descriptor-identity sha256 (64-char lowercase hex) of the DslDescriptor the run '
    'executed — NOT full logic identity; true content hash is a follow-up. NULL for '
    'historical rows and runs with an unresolvable definition.';
