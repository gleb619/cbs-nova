package cbs.nova.starter.model;

import cbs.nova.dsl.model.PreviewReport;

/**
 * One author-defined test case attached to a DSL definition (T409). Used both for reading/writing
 * the case set ({@code GET/PUT /api/dsl/definitions/{name}/tests}). {@code input} is the example
 * request fed to the preview pipeline; {@code expectedOutput} is the expected preview report.
 */
public record DefinitionTestCase(
        String caseName,
        DslRequest input,
        PreviewReport expectedOutput) {
}
