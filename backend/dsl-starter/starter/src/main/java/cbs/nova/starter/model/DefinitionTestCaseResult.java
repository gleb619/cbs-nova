package cbs.nova.starter.model;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.dsl.model.CompileDiagnostic;

import cbs.nova.dsl.model.PreviewReport;
import org.jspecify.annotations.Nullable;

public record DefinitionTestCaseResult(
        String name,
        DefinitionTestCaseStatus status,
        @Nullable PreviewReport actual,
        PreviewReport expected,
        long durationMs,
        @Nullable ErrorResponse diagnostics) {
}
