CREATE TABLE mass_operation_item (
    id                          BIGSERIAL    PRIMARY KEY,
    mass_operation_execution_id BIGINT       NOT NULL REFERENCES mass_operation_execution(id),
    item_key                    VARCHAR(500) NOT NULL,
    item_data                   JSONB        NOT NULL DEFAULT '{}',
    status                      VARCHAR(20)  NOT NULL,
    workflow_execution_id       BIGINT       REFERENCES workflow_execution(id),
    error_message               TEXT,
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    retry_of                    BIGINT       REFERENCES mass_operation_item(id)
);

CREATE INDEX idx_mass_op_item_exec   ON mass_operation_item(mass_operation_execution_id);
CREATE INDEX idx_mass_op_item_status ON mass_operation_item(status);
CREATE INDEX idx_mass_op_item_key    ON mass_operation_item(item_key);
