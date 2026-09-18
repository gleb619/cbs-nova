package cbs.nova.starter.core.stage;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.pipe.HierarchyAccumulators;
import cbs.nova.starter.logging.DryRunLogBuffer;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogEvent;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

@RequiredArgsConstructor
public final class DryRunLogStage implements DslPipeStage {

  private final DryRunLoggingContext dryRunLoggingContext;
  private final DryRunLogBufferRegistry bufferRegistry;
  private final int maxEventsPerRun;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    if (context.mode() == ExecutionMode.RUN) {
      return next.proceed(context);
    }

    Deque<DryRunLogEvent> queue = new LinkedBlockingDeque<>(maxEventsPerRun);
    DryRunLogBuffer buffer = new DryRunLogBuffer(maxEventsPerRun, queue);
    String runId = context.runId();
    bufferRegistry.register(runId, buffer);
    context.setAttribute(StarterConstants.DRY_RUN_LOG_BUFFER_ATTRIBUTE, buffer);
    dryRunLoggingContext.setRunId(runId);

    try {
      return next.proceed(context);
    } finally {
      List<DryRunLogEvent> events = buffer.drain();
      List<Map<String, Object>> logs = toDryRunLogMaps(events);
      var accumulator = HierarchyAccumulators.resolve(context);
      if (accumulator.isPresent()) {
        accumulator.get().dryRunLogs(logs);
      } else {
        context.setAttribute(StarterConstants.DRY_RUN_LOGS_ATTRIBUTE, logs);
      }
      bufferRegistry.remove(runId);
      dryRunLoggingContext.clearRunId();
    }
  }

  private @NonNull List<Map<String, Object>> toDryRunLogMaps(@NonNull List<DryRunLogEvent> events) {
    List<Map<String, Object>> maps = new ArrayList<>();
    for (DryRunLogEvent event : events) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put(StarterConstants.PAYLOAD_LEVEL, event.level());
      map.put(StarterConstants.PAYLOAD_MESSAGE, event.message());
      map.put(StarterConstants.PAYLOAD_TIMESTAMP, Instant.ofEpochMilli(event.timestampMillis()));
      map.put(StarterConstants.PAYLOAD_MDC, event.mdc());
      map.put(StarterConstants.PAYLOAD_RUN_ID, event.runId());
      maps.add(map);
    }
    return List.copyOf(maps);
  }
}
