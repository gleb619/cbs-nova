CREATE TABLE IF NOT EXISTS dsl_definition_tests (
    id BIGSERIAL PRIMARY KEY,
    definition_name VARCHAR(200) NOT NULL,
    case_name VARCHAR(200) NOT NULL,
    input JSONB NOT NULL,
    expected_output JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Author-defined example inputs + expected outputs stored per definition; a case is uniquely
-- identified within a definition so authoring can replace the whole set in one transaction.
CREATE UNIQUE INDEX IF NOT EXISTS uq_dsl_definition_tests_name_case
    ON dsl_definition_tests (definition_name, case_name);
CREATE INDEX IF NOT EXISTS idx_dsl_definition_tests_definition_name
    ON dsl_definition_tests (definition_name);