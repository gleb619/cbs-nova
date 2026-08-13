package cbs.nova.starter.core.stage;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.logging.DryRunLogBuffer;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogEvent;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    DryRunLogBuffer buffer = new DryRunLogBuffer(maxEventsPerRun);
    String runId = context.getRunId();
    bufferRegistry.register(runId, buffer);
    context.setAttribute("dryRunLogBuffer", buffer);
    dryRunLoggingContext.setRunId(runId);

    try {
      return next.proceed(context);
    } finally {
      List<DryRunLogEvent> events = buffer.drain();
      context.setAttribute("dryRunLogs", toDryRunLogMaps(events));
      bufferRegistry.remove(runId);
      dryRunLoggingContext.clearRunId();
    }
  }

  private @NonNull List<Map<String, Object>> toDryRunLogMaps(@NonNull List<DryRunLogEvent> events) {
    List<Map<String, Object>> maps = new ArrayList<>();
    for (DryRunLogEvent event : events) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("level", event.level());
      map.put("message", event.message());
      map.put("timestamp", Instant.ofEpochMilli(event.timestampMillis()));
      map.put("mdc", event.mdc());
      map.put("runId", event.runId());
      maps.add(map);
    }
    return List.copyOf(maps);
  }
}
