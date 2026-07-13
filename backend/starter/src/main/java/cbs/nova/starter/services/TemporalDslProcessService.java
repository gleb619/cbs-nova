package cbs.nova.starter.services;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.DslRunRepository;
import cbs.nova.dsl.DslRunStatus;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Service-layer API for running DSL processes backed by Temporal. It keeps callers decoupled from
 * generated workflow class names: callers provide the logical DSL process name and the service
 * delegates to {@link GlobalManager}, which routes to the runner and, when a
 * {@link cbs.nova.dsl.TemporalProcessLauncher} is registered, launches a Temporal workflow under
 * the hood.
 *
 * <p>
 * Every run is recorded through {@link DslRunRepository} so executions can be audited later.
 */
@Service
public class TemporalDslProcessService {

  private final ContextFactory contextFactory;
  private final DslRunRepository runRepository;
  private final ObjectMapper objectMapper;

  public TemporalDslProcessService(@NonNull ContextFactory contextFactory) {
    this(contextFactory, new InMemoryDslRunRepository(), new ObjectMapper());
  }

  @Autowired
  public TemporalDslProcessService(
          @NonNull ContextFactory contextFactory,
          @NonNull DslRunRepository runRepository,
          @NonNull ObjectMapper objectMapper) {
    this.contextFactory = contextFactory;
    this.runRepository = runRepository;
    this.objectMapper = objectMapper;
  }

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

    DslRun running = new DslRun(
            runId,
            processName,
            DslRunStatus.RUNNING.name(),
            inputJson,
            null,
            null,
            startedAt,
            null,
            ExecutionMode.RUN.name());
    runRepository.save(running);

    Context<?> ctx = contextFactory.of(body, metadata, ExecutionMode.RUN, runId);
    Result<?> result;
    try {
      result = GlobalManager.getInstance().runProcess(processName, ctx);
    } catch (Exception ex) {
      result = Result.failure(ex);
    }

    Instant finishedAt = Instant.now();
    DslRun finished = new DslRun(
            runId,
            processName,
            result.isSuccess() ? DslRunStatus.COMPLETED.name() : DslRunStatus.FAILED.name(),
            inputJson,
            result.isSuccess() ? serialize(result.value()) : null,
            result.isSuccess() ? null : messageOf(result.cause()),
            startedAt,
            finishedAt,
            ExecutionMode.RUN.name());
    runRepository.save(finished);

    return result;
  }

  private @NonNull String serialize(@Nullable Object value) {
    if (value == null) {
      return "null";
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize run payload", e);
    }
  }

  private @Nullable String messageOf(@Nullable Throwable cause) {
    return cause != null
            ? (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName())
            : "unknown";
  }
}
