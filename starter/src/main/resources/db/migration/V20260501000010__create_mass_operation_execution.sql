CREATE TABLE mass_operation_execution (
    id                   BIGSERIAL     PRIMARY KEY,
    code                 VARCHAR(100)  NOT NULL,
    category             VARCHAR(100)  NOT NULL,
    dsl_version          VARCHAR(50)   NOT NULL,
    status               VARCHAR(30)   NOT NULL,
    context              JSONB         NOT NULL DEFAULT '{}',
    total_items          BIGINT        NOT NULL DEFAULT 0,
    processed_count      BIGINT        NOT NULL DEFAULT 0,
    failed_count         BIGINT        NOT NULL DEFAULT 0,
    trigger_type         VARCHAR(50)   NOT NULL,
    trigger_source       VARCHAR(200),
    performed_by         VARCHAR(200)  NOT NULL,
    started_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,
    temporal_workflow_id VARCHAR(200)
);

CREATE INDEX idx_mass_op_exec_code     ON mass_operation_execution(code);
CREATE INDEX idx_mass_op_exec_status   ON mass_operation_execution(status);
CREATE INDEX idx_mass_op_exec_category ON mass_operation_execution(category);
