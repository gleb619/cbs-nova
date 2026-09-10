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
