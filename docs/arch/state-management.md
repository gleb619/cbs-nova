# State Management

← [Back to TDD](../tdd.md)

## 8.1 PostgreSQL Schema

All JSONB fields containing business data are encrypted at the application level.

```sql
-- Unchanged from v0.4
CREATE TABLE workflow_execution (
    id                BIGSERIAL    PRIMARY KEY,
    workflow_code     VARCHAR(100) NOT NULL,
    dsl_version       VARCHAR(50)  NOT NULL,
    current_state     VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL,        -- ACTIVE / CLOSED / FAULTED
    context           JSONB        NOT NULL DEFAULT '{}',        -- encrypted
    display_data      JSONB        NOT NULL DEFAULT '{}',        -- encrypted
    performed_by      VARCHAR(200) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE event_execution (
    id                    BIGSERIAL    PRIMARY KEY,
    event_code            VARCHAR(100) NOT NULL,
    dsl_version           VARCHAR(50)  NOT NULL,
    action                VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    context               JSONB        NOT NULL DEFAULT '{}',    -- encrypted
    executed_transactions JSONB        NOT NULL DEFAULT '[]',    -- encrypted
    temporal_workflow_id  VARCHAR(200),
    workflow_execution_id BIGINT       NOT NULL REFERENCES workflow_execution(id),
    performed_by          VARCHAR(200) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ
);

CREATE TABLE workflow_transition_log (
    id                    BIGSERIAL    PRIMARY KEY,
    workflow_execution_id BIGINT       NOT NULL REFERENCES workflow_execution(id),
    event_execution_id    BIGINT       REFERENCES event_execution(id),
    action                VARCHAR(20)  NOT NULL,
    from_state            VARCHAR(100) NOT NULL,
    to_state              VARCHAR(100),
    status                VARCHAR(20)  NOT NULL,    -- RUNNING / COMPLETED / FAULTED
    fault_message         TEXT,
    dsl_version           VARCHAR(50)  NOT NULL,
    performed_by          VARCHAR(200) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ
);

CREATE INDEX idx_workflow_execution_code    ON workflow_execution(workflow_code);
CREATE INDEX idx_workflow_execution_status  ON workflow_execution(status);
CREATE INDEX idx_workflow_execution_user    ON workflow_execution(performed_by);
CREATE INDEX idx_transition_log_workflow_id ON workflow_transition_log(workflow_execution_id);
CREATE INDEX idx_transition_log_status      ON workflow_transition_log(status);
CREATE INDEX idx_transition_log_user        ON workflow_transition_log(performed_by);
CREATE INDEX idx_event_execution_workflow   ON event_execution(workflow_execution_id);
CREATE INDEX idx_event_execution_user       ON event_execution(performed_by);

-- NEW: one row per mass operation run
CREATE TABLE mass_operation_execution (
    id                BIGSERIAL    PRIMARY KEY,
    code              VARCHAR(100) NOT NULL,
    category          VARCHAR(100) NOT NULL,
    dsl_version       VARCHAR(50)  NOT NULL,
    status            VARCHAR(30)  NOT NULL,  -- RUNNING / DONE / DONE_WITH_FAILURES / LOCKED / FAULTED
    context           JSONB        NOT NULL DEFAULT '{}',   -- encrypted, shared for all items
    total_items       BIGINT       NOT NULL DEFAULT 0,
    processed_count   BIGINT       NOT NULL DEFAULT 0,
    failed_count      BIGINT       NOT NULL DEFAULT 0,
    trigger_type      VARCHAR(50)  NOT NULL,  -- CRON / ONCE / SIGNAL_EXTERNAL / SIGNAL_FROM_OP
    trigger_source    VARCHAR(200),           -- signal source or cron expression
    performed_by      VARCHAR(200) NOT NULL,  -- user or system/scheduler
    started_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    temporal_workflow_id VARCHAR(200)
);

-- NEW: one row per item per run
CREATE TABLE mass_operation_item (
    id                       BIGSERIAL    PRIMARY KEY,
    mass_operation_execution_id BIGINT    NOT NULL REFERENCES mass_operation_execution(id),
    item_key                 VARCHAR(500) NOT NULL,  -- business identifier (e.g. agreement_id)
    item_data                JSONB        NOT NULL DEFAULT '{}',  -- encrypted source row
    status                   VARCHAR(20)  NOT NULL,  -- PENDING / RUNNING / DONE / FAILED
    workflow_execution_id    BIGINT       REFERENCES workflow_execution(id),
    error_message            TEXT,
    started_at               TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    retry_of                 BIGINT       REFERENCES mass_operation_item(id)  -- for retried items
);

CREATE INDEX idx_mass_op_exec_code     ON mass_operation_execution(code);
CREATE INDEX idx_mass_op_exec_status   ON mass_operation_execution(status);
CREATE INDEX idx_mass_op_exec_category ON mass_operation_execution(category);
CREATE INDEX idx_mass_op_item_exec     ON mass_operation_item(mass_operation_execution_id);
CREATE INDEX idx_mass_op_item_status   ON mass_operation_item(status);
CREATE INDEX idx_mass_op_item_key      ON mass_operation_item(item_key);
```

## 8.2 Temporal Payload

Temporal holds only:
- `event_execution.id`
- `workflow_execution.id`
- `mass_operation_execution.id`
- `mass_operation_item.id` (per item activity)

All real state lives in PostgreSQL.

## 8.3 Context Evaluation (Pre-Temporal)

1. Load accumulated `workflow_execution.context` (decrypt, deserialize)
2. Seed `EnrichmentContext` — parameters from API request merged on top
3. Evaluate each `ctx[key] = ...` in the `context {}` block in order
4. Helper calls in `context {}` are synchronous Spring bean calls (no Temporal)
5. Persist enriched context back to `workflow_execution.context` (encrypt, serialize)
6. On failure → `FAULTED` status, HTTP `CONTEXT_FAULT` error, stop
