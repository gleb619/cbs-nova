package cbs.nova.starter.model;

import java.util.List;

/** Aggregate result of running a definition's stored test cases (T409). */
public record DefinitionTestRunReport(
        int total,
        int passed,
        int failed,
        int errored,
        List<DefinitionTestCaseResult> cases) {
}
