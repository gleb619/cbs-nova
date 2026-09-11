package cbs.nova.starter.model;

import cbs.nova.dsl.model.PreviewReport;
import org.jspecify.annotations.Nullable;

/**
 * Outcome of executing a single stored test case against the preview pipeline (T409).
 *
 * @param status
 *          {@code PASS} (actual output deep-equals expected output) | {@code FAIL} (mismatch) |
 *          {@code ERROR} (preview threw or returned an error outcome).
 * @param actual
 *          the preview report when available, {@code null} on {@code ERROR}.
 * @param expected
 *          the stored expected preview report.
 * @param durationMs
 *          execution time of the preview call for this case.
 * @param diagnostics
 *          populated on {@code ERROR} with the error body.
 */
public record DefinitionTestCaseResult(
        String name,
        DefinitionTestCaseStatus status,
        @Nullable PreviewReport actual,
        PreviewReport expected,
        long durationMs,
        @Nullable ErrorResponse diagnostics) {
}
