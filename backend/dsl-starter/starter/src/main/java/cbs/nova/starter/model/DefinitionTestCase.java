package cbs.nova.starter.model;

import cbs.nova.dsl.model.PreviewReport;


public record DefinitionTestCase(
        String caseName,
        DslRequest input,
        PreviewReport expectedOutput) {
}
