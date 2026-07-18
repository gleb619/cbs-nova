package cbs.nova.starter.services;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunRepository;
import cbs.nova.dsl.DslRunStatus;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

@AllArgsConstructor
public class TemporalDslProcessService {

  private final ContextFactory contextFactory;
  private final DslRunRepository runRepository;
  private final ObjectMapper objectMapper;

  public @NonNull Result<?> runProcess(@NonNull String processName, @Nullable Object input) {
    return runProcess(processName, input, Map.of());
  }

  public @NonNull Result<?> runProcess(
          @NonNull String processName,
          @Nullable Object input,
          @NonNull Map<String, Object> metadata) {
    Object body = input != null ? input : Map.of();
    String runId = contextFactory.generateRunId();
    String inputJson = serialize(body);
    Instant startedAt = Instant.now();

    DslRun running = DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(DslRunStatus.RUNNING.name())
            .input(inputJson)
            .output(null)
            .error(null)
            .startedAt(startedAt)
            .finishedAt(null)
            .executionMode(ExecutionMode.RUN.name())
            .build();
    runRepository.save(running);

    Context<?> ctx = contextFactory.of(body, metadata, ExecutionMode.RUN, runId);
    Result<?> result;
    try {
      result = GlobalManager.globalManager().runProcess(processName, ctx);
    } catch (Exception ex) {
      result = Result.failure(ex);
    }

    Instant finishedAt = Instant.now();
    DslRun finished = DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(result.isSuccess() ? DslRunStatus.COMPLETED.name() : DslRunStatus.FAILED.name())
            .input(inputJson)
            .output(result.isSuccess() ? serialize(result.value()) : null)
            .error(result.isSuccess() ? null : messageOf(result.cause()))
            .startedAt(startedAt)
            .finishedAt(finishedAt)
            .executionMode(ExecutionMode.RUN.name())
            .build();
    runRepository.save(finished);

    return result;
  }

  private @NonNull String serialize(@Nullable Object value) {
    if (value == null) {
      return "null";
    }

    return objectMapper.writeValueAsString(value);
  }

  private @Nullable String messageOf(@Nullable Throwable cause) {
    return cause != null
            ? (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName())
            : "unknown";
  }
}
