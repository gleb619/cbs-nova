package cbs.nova.starter.model;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Execution detail DTO returned by the executions list and detail endpoints.
 *
 * <p>
 * Field set (the wire shape is the API source of truth — no separate OpenAPI document is generated
 * for this DTO):
 * <ul>
 * <li>{@code trace} — populated only by {@link #fromDetail} when the run row carries a parseable
 * {@code context_json} with a {@code "trace"} array (defensive: malformed / absent / empty →
 * {@code null}, never throws). Mapped from the flat string list by {@link #toTraceSteps}.
 * <li>{@code logs} — not modelled here; the backend currently has no log source for production
 * runs, so the FE renders the Logs tab only when a custom payload supplies one.
 * </ul>
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
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ErrorEntry> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<TraceStepDto> trace) {

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
            run.triggeredBy(),
            run.correlationId(),
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
            toErrors(run.error()),
            toTraceSteps(run.contextJson(), objectMapper));
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

  /**
   * Maximum number of trace steps surfaced to the FE before we append a synthetic truncation
   * marker. Bounded so a runaway construct cannot push an arbitrarily large payload down the wire.
   */
  static final int TRACE_STEP_CAP = 500;

  static final String COMPENSATION_PREFIX = "compensation log: ";
  static final String HELPER_PREFIX = "called helper: ";
  static final String TX_EXECUTED_PREFIX = "executed transaction: ";
  static final String TX_CALLED_PREFIX = "called transaction: ";
  static final String TRUNCATION_NAME_FORMAT = "… trace truncated (%d entries)";

  /**
   * Decode the persisted {@code context_json} blob into FE-shaped trace steps.
   *
   * <p>
   * Tolerant on every failure path: absent, malformed, or absent {@code trace} array → {@code null}
   * (never throws, never returns an empty list — the FE renders the empty state only when
   * {@code trace} is null).
   *
   * <p>
   * Mapping rules per entry string:
   * <ul>
   * <li>{@code "called helper: <name>"} → stepType {@code "Helper"}, name {@code <name>}
   * <li>{@code "executed transaction: <name>"} or {@code "called transaction: <name>"} → stepType
   * {@code "Transaction"}, name {@code <name>}
   * <li>{@code "compensation log: <msg>"} → stepType {@code "Process"}, name {@code <msg>}; also
   * marks the start of the compensation phase
   * <li>anything else → stepType {@code "Process"}, name = raw string
   * </ul>
   *
   * <p>
   * {@code isCompensation} flips to {@code true} for every entry at or after the first
   * {@code "compensation log:"} entry. The trace is flat (no nesting, no per-entry phase tag) so
   * this is a heuristic — documented on {@link TraceStepDto}.
   */
  static List<TraceStepDto> toTraceSteps(@org.jspecify.annotations.Nullable String contextJson,
          ObjectMapper objectMapper) {
    if (contextJson == null || contextJson.isBlank()) {
      return null;
    }
    Object parsed;
    try {
      parsed = objectMapper.readValue(contextJson, Object.class);
    } catch (Exception malformed) {
      return null;
    }
    if (!(parsed instanceof Map<?, ?> map)) {
      return null;
    }
    Object rawList = map.get("trace");
    if (!(rawList instanceof List<?> list)) {
      return null;
    }
    List<String> entries = new ArrayList<>(list.size());
    for (Object item : list) {
      if (item != null) {
        entries.add(item.toString());
      }
    }
    if (entries.isEmpty()) {
      return null;
    }
    return mapSteps(entries);
  }

  private static List<TraceStepDto> mapSteps(List<String> entries) {
    List<TraceStepDto> out = new ArrayList<>(Math.min(entries.size(), TRACE_STEP_CAP + 1));
    boolean compensating = false;
    int emitted = 0;
    for (int i = 0; i < entries.size() && emitted < TRACE_STEP_CAP; i++, emitted++) {
      String raw = entries.get(i);
      StepMapping mapping = classify(raw);
      if (mapping.startsCompensationPhase) {
        compensating = true;
      }
      out.add(new TraceStepDto(
              String.valueOf(i),
              mapping.stepType,
              mapping.name,
              compensating));
    }
    if (entries.size() > TRACE_STEP_CAP) {
      out.add(new TraceStepDto(
              String.valueOf(TRACE_STEP_CAP),
              "Process",
              String.format(TRUNCATION_NAME_FORMAT, entries.size()),
              compensating));
    }
    return Collections.unmodifiableList(out);
  }

  private record StepMapping(String stepType, String name, boolean startsCompensationPhase) {

  }

  private static StepMapping classify(String raw) {
    if (raw == null) {
      return new StepMapping("Process", "", false);
    }
    if (raw.startsWith(COMPENSATION_PREFIX)) {
      return new StepMapping("Process", raw.substring(COMPENSATION_PREFIX.length()), true);
    }
    if (raw.startsWith(HELPER_PREFIX)) {
      return new StepMapping("Helper", raw.substring(HELPER_PREFIX.length()), false);
    }
    if (raw.startsWith(TX_EXECUTED_PREFIX)) {
      return new StepMapping("Transaction", raw.substring(TX_EXECUTED_PREFIX.length()), false);
    }
    if (raw.startsWith(TX_CALLED_PREFIX)) {
      return new StepMapping("Transaction", raw.substring(TX_CALLED_PREFIX.length()), false);
    }
    return new StepMapping("Process", raw, false);
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
