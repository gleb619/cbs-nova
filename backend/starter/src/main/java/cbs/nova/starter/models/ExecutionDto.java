package cbs.nova.starter.models;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Wire DTO for a single DSL execution run.
 *
 * <p>
 * The JSON shape mirrors the frontend {@code Execution}/{@code ExecutionDetail} interfaces byte for
 * byte on field names and enum casing so the BFF can pass the payload through unchanged. Optional
 * fields are omitted from the payload when absent via {@code @JsonInclude(NON_NULL)}, matching the
 * optional {@code ?} properties of the frontend type.
 *
 * <p>
 * {@code entityType} is always {@code "Process"}: {@link DslRun} does not track whether a run is a
 * process, transaction or helper today, so {@code Process} is the best available default (known
 * limitation).
 *
 * <p>
 * {@code input}/{@code output}/{@code errors} are detail-only fields (populated via
 * {@link #fromDetail(DslRun, ObjectMapper)}, used by {@code GET /api/executions/{id}}). The list
 * endpoint (@code GET /api/executions}) uses {@link #from(DslRun)}, which leaves them {@code null}
 * so they're omitted from the response, keeping the list payload lean. {@code trace}, {@code logs}
 * and {@code mermaidDiagram} from the frontend {@code ExecutionDetail} type are intentionally not
 * modeled here: they are not captured anywhere in the backend for {@code RUN}-mode executions today
 * (see T204 plan) and are left out rather than faked.
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
        @JsonInclude(JsonInclude.Include.NON_NULL) String workflowId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object input,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object output,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ErrorEntry> errors) {

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
            null,
            null,
            null,
            null);
  }

  /**
   * Maps a {@link DslRun} for the execution detail endpoint, additionally surfacing
   * {@code input}/{@code output} (parsed as JSON when the stored string is valid JSON, otherwise
   * passed through as the raw string) and {@code errors} (a single-element list built from
   * {@link DslRun#error()}, or an empty list when there is no error).
   */
  public static ExecutionDto fromDetail(DslRun run, ObjectMapper objectMapper) {
    ExecutionDto base = from(run);
    return new ExecutionDto(
            base.id(),
            base.entity(),
            base.entityType(),
            base.mode(),
            base.status(),
            base.startedAt(),
            base.completedAt(),
            base.duration(),
            base.retries(),
            base.triggeredBy(),
            base.correlationId(),
            base.workflowId(),
            parseJsonOrRaw(run.input(), objectMapper),
            parseJsonOrRaw(run.output(), objectMapper),
            toErrors(run.error()));
  }

  private static Object parseJsonOrRaw(String raw, ObjectMapper objectMapper) {
    if (raw == null) {
      return null;
    }
    try {
      return objectMapper.readValue(raw, Object.class);
    } catch (Exception malformedJson) {
      // Stored value isn't valid JSON (or isn't JSON at all) — fall back to the raw string rather
      // than failing the request.
      return raw;
    }
  }

  private static List<ErrorEntry> toErrors(String error) {
    if (error == null) {
      return List.of();
    }
    return List.of(new ErrorEntry(error, null, null));
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
