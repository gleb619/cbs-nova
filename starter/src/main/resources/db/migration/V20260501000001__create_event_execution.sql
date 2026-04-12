CREATE TABLE event_execution (
    id                    BIGSERIAL    PRIMARY KEY,
    event_code            VARCHAR(100) NOT NULL,
    dsl_version           VARCHAR(50)  NOT NULL,
    action                VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    context               JSONB        NOT NULL DEFAULT '{}',
    executed_transactions JSONB        NOT NULL DEFAULT '[]',
    temporal_workflow_id  VARCHAR(200),
    workflow_execution_id BIGINT       NOT NULL REFERENCES workflow_execution(id),
    performed_by          VARCHAR(200) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ
);

CREATE INDEX idx_event_execution_workflow ON event_execution(workflow_execution_id);
CREATE INDEX idx_event_execution_user     ON event_execution(performed_by);
