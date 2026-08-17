package cbs.nova.starter.logging;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class DryRunLoggingContextPropagator implements ContextPropagator {

  private final DryRunLoggingContext dryRunLoggingContext;

  @Override
  public String getName() {
    return "cbs-nova-dry-run";
  }

  @Override
  public Object getCurrentContext() {
    String runId = dryRunLoggingContext.currentRunId();
    if (runId == null) {
      return Collections.emptyMap();
    }
    return Map.of(DryRunLoggingContext.RUN_ID_HEADER, runId);
  }

  @Override
  public void setCurrentContext(Object context) {
    @SuppressWarnings("unchecked")
    Map<String, String> map = (Map<String, String>) context;
    String runId = map.get(DryRunLoggingContext.RUN_ID_HEADER);
    if (runId != null) {
      dryRunLoggingContext.setRunId(runId);
      MDC.put(DryRunLoggingContext.RUN_ID_HEADER, runId);
    } else {
      dryRunLoggingContext.clearRunId();
      MDC.remove(DryRunLoggingContext.RUN_ID_HEADER);
    }
  }

  @Override
  public Map<String, Payload> serializeContext(Object context) {
    @SuppressWarnings("unchecked")
    Map<String, String> map = (Map<String, String>) context;
    Map<String, Payload> serialized = new HashMap<>();
    DataConverter converter = DataConverter.getDefaultInstance();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      converter.toPayload(entry.getValue())
              .ifPresent(payload -> serialized.put(entry.getKey(), payload));
    }
    return serialized;
  }

  @Override
  public Object deserializeContext(Map<String, Payload> header) {
    Map<String, String> map = new HashMap<>();
    DataConverter converter = DataConverter.getDefaultInstance();
    for (Map.Entry<String, Payload> entry : header.entrySet()) {
      if (DryRunLoggingContext.RUN_ID_HEADER.equals(entry.getKey())) {
        String runId = converter.fromPayload(entry.getValue(), String.class, String.class);
        map.put(entry.getKey(), runId);
      }
    }
    return map;
  }
}
