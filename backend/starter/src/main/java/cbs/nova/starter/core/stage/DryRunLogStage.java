package cbs.nova.starter.core.stage;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.logging.DryRunLogEvent;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import ch.qos.logback.classic.Logger;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class DryRunLogStage implements DslPipeStage {

  private final DryRunLoggingContext dryRunLoggingContext;

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    if (context.getMode() == ExecutionMode.RUN) {
      return next.proceed(context);
    }
    dryRunLoggingContext.setRunId(context.getRunId());
    try {
      return next.proceed(context);
    } finally {
      List<DryRunLogEvent> events = drainDryRunLogs(context.getRunId());
      context.setAttribute("dryRunLogs", toDryRunLogMaps(events));
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

  private @NonNull List<DryRunLogEvent> drainDryRunLogs(@NonNull String runId) {
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender = root
            .getAppender("DRY_RUN");
    if (appender instanceof DryRunLogbackAppender dryRunAppender) {
      return dryRunAppender.drain(runId);
    }
    return List.of();
  }
}
