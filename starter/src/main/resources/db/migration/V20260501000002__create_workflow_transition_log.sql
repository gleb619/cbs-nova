CREATE TABLE workflow_transition_log (
    id                    BIGSERIAL    PRIMARY KEY,
    workflow_execution_id BIGINT       NOT NULL REFERENCES workflow_execution(id),
    event_execution_id    BIGINT       REFERENCES event_execution(id),
    action                VARCHAR(20)  NOT NULL,
    from_state            VARCHAR(100) NOT NULL,
    to_state              VARCHAR(100),
    status                VARCHAR(20)  NOT NULL,
    fault_message         TEXT,
    dsl_version           VARCHAR(50)  NOT NULL,
    performed_by          VARCHAR(200) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ
);

CREATE INDEX idx_transition_log_workflow_id ON workflow_transition_log(workflow_execution_id);
CREATE INDEX idx_transition_log_status      ON workflow_transition_log(status);
CREATE INDEX idx_transition_log_user        ON workflow_transition_log(performed_by);
