CREATE TABLE workflow_execution (
    id                BIGSERIAL    PRIMARY KEY,
    workflow_code     VARCHAR(100) NOT NULL,
    dsl_version       VARCHAR(50)  NOT NULL,
    current_state     VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    context           JSONB        NOT NULL DEFAULT '{}',
    display_data      JSONB        NOT NULL DEFAULT '{}',
    performed_by      VARCHAR(200) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_execution_code   ON workflow_execution(workflow_code);
CREATE INDEX idx_workflow_execution_status ON workflow_execution(status);
CREATE INDEX idx_workflow_execution_user   ON workflow_execution(performed_by);
