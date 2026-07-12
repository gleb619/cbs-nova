package cbs.nova.starter.services;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service-layer API for running DSL processes backed by Temporal. It keeps callers decoupled from
 * generated workflow class names: callers provide the logical DSL process name and the service
 * delegates to {@link GlobalManager}, which routes to the runner and, when a
 * {@link cbs.nova.dsl.TemporalProcessLauncher} is registered, launches a Temporal workflow under
 * the hood.
 */
@Service
@RequiredArgsConstructor
public class TemporalDslProcessService {

  private final ContextFactory contextFactory;

  public @NonNull Result<?> runProcess(@NonNull String processName, @Nullable Object input) {
    return runProcess(processName, input, Map.of());
  }

  public @NonNull Result<?> runProcess(
          @NonNull String processName,
          @Nullable Object input,
          @NonNull Map<String, Object> metadata) {
    Object body = input != null ? input : Map.of();
    Context<?> ctx = contextFactory.of(body, metadata, ExecutionMode.RUN,
            contextFactory.generateRunId());
    return GlobalManager.getInstance().runProcess(processName, ctx);
  }
}
