package cbs.nova.starter.model;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Outcome of executing a single stored test case against the preview pipeline (T409).
 *
 * @param status
 *          {@code PASS} (actual deep-equals expected) | {@code FAIL} (mismatch) | {@code ERROR}
 *          (preview threw or returned an error outcome).
 * @param actual
 *          the preview result body when available, {@code null} on {@code ERROR}.
 * @param expected
 *          the stored expected output.
 * @param durationMs
 *          execution time of the preview call for this case.
 * @param diagnostics
 *          optional detail; populated on {@code ERROR} with the error body.
 */
public record DefinitionTestCaseResult(
        String name,
        String status,
        @Nullable JsonNode actual,
        JsonNode expected,
        long durationMs,
        @Nullable Object diagnostics) {

  public static final String STATUS_PASS = "PASS";
  public static final String STATUS_FAIL = "FAIL";
  public static final String STATUS_ERROR = "ERROR";
}
