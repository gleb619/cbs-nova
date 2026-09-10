package cbs.nova.starter.model;

import tools.jackson.databind.JsonNode;

/**
 * One author-defined test case attached to a DSL definition (T409). Used both for reading/writing
 * the case set ({@code GET/PUT /api/dsl/definitions/{name}/tests}) — {@code input} is the example
 * input fed to the preview pipeline, {@code expectedOutput} is its expected result.
 */
public record DefinitionTestCase(
        String caseName,
        JsonNode input,
        JsonNode expectedOutput) {
}
