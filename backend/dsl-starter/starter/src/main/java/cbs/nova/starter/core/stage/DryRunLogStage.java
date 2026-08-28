package cbs.nova.starter.core.stage;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.core.StarterConstant;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
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
    if (context.getMode() == ExecutionMode.RUN) {
      return next.proceed(context);
    }

    Deque<DryRunLogEvent> queue = new LinkedBlockingDeque<>(maxEventsPerRun);
    DryRunLogBuffer buffer = new DryRunLogBuffer(maxEventsPerRun, queue);
    String runId = context.getRunId();
    bufferRegistry.register(runId, buffer);
    context.setAttribute(StarterConstant.DRY_RUN_LOG_BUFFER_ATTRIBUTE, buffer);
    dryRunLoggingContext.setRunId(runId);

    try {
      return next.proceed(context);
    } finally {
      List<DryRunLogEvent> events = buffer.drain();
      context.setAttribute(StarterConstant.DRY_RUN_LOGS_ATTRIBUTE, toDryRunLogMaps(events));
      bufferRegistry.remove(runId);
      dryRunLoggingContext.clearRunId();
    }
  }

  private @NonNull List<Map<String, Object>> toDryRunLogMaps(@NonNull List<DryRunLogEvent> events) {
    List<Map<String, Object>> maps = new ArrayList<>();
    for (DryRunLogEvent event : events) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put(StarterConstant.PAYLOAD_LEVEL, event.level());
      map.put(StarterConstant.PAYLOAD_MESSAGE, event.message());
      map.put(StarterConstant.PAYLOAD_TIMESTAMP, Instant.ofEpochMilli(event.timestampMillis()));
      map.put(StarterConstant.PAYLOAD_MDC, event.mdc());
      map.put(StarterConstant.PAYLOAD_RUN_ID, event.runId());
      maps.add(map);
    }
    return List.copyOf(maps);
  }
}
