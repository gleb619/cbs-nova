package cbs.nova.starter.models;

import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Wire DTO for a single DSL execution run.
 *
 * <p>
 * The JSON shape mirrors the frontend {@code Execution} interface byte for byte on field names and
 * enum casing so the BFF can pass the payload through unchanged. Optional fields are omitted from
 * the payload when absent via {@code @JsonInclude(NON_NULL)}, matching the optional {@code ?}
 * properties of the frontend type.
 *
 * <p>
 * {@code entityType} is always {@code "Process"}: {@link DslRun} does not track whether a run is a
 * process, transaction or helper today, so {@code Process} is the best available default (known
 * limitation).
 */
public record ExecutionDto(
        String id,
        String entity,
        String entityType,
        String mode,
        String status,
        String startedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String completedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long duration,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer retries,
        @JsonInclude(JsonInclude.Include.NON_NULL) String triggeredBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) String correlationId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String workflowId) {

  public static ExecutionDto from(DslRun run) {
    Instant finishedAt = run.finishedAt();
    return new ExecutionDto(
            run.runId(),
            run.processName(),
            "Process",
            toExecutionMode(run.executionMode()),
            toExecutionStatus(run.status()),
            run.startedAt().toString(),
            finishedAt != null ? finishedAt.toString() : null,
            finishedAt != null ? Duration.between(run.startedAt(), finishedAt).toMillis() : null,
            null,
            null,
            null,
            null);
  }

  private static String toExecutionMode(String mode) {
    return mode == null || mode.isBlank() ? "RUN" : mode.toUpperCase(Locale.ROOT);
  }

  private static String toExecutionStatus(String status) {
    if (status == null || status.isBlank()) {
      return "Running";
    }
    for (DslRunStatus candidate : DslRunStatus.values()) {
      if (candidate.name().equals(status)) {
        return capitalize(candidate.name());
      }
    }
    // Unknown statuses (e.g. PENDING/COMPENSATED once emitted) are passed through capitalized.
    return capitalize(status);
  }

  private static String capitalize(String value) {
    return value.substring(0, 1).toUpperCase(Locale.ROOT)
            + value.substring(1).toLowerCase(Locale.ROOT);
  }
}
