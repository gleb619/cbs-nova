package cbs.nova.starter.model;

import java.util.List;


public record DefinitionTestRunReport(
        int total,
        int passed,
        int failed,
        int errored,
        List<DefinitionTestCaseResult> cases) {
}
