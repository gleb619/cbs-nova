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
