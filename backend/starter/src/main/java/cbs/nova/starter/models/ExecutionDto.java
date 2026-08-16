package cbs.nova.starter.models;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

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
    return capitalize(status);
  }

  private static String capitalize(String value) {
    return value.substring(0, 1).toUpperCase(Locale.ROOT)
            + value.substring(1).toLowerCase(Locale.ROOT);
  }
}
