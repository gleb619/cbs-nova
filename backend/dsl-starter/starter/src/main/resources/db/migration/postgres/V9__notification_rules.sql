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
