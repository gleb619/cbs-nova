package cbs.nova.starter.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Propagates the OpenTelemetry W3C trace context through Temporal workflow/activity execution so
 * that worker-side spans join the same trace as the initiating DSL run.
 */
@RequiredArgsConstructor
public class OpenTelemetryContextPropagator implements ContextPropagator {

  private static final String NAME = "cbs-nova-otel-trace";

  private final OpenTelemetry openTelemetry;

  private final TextMapSetter<Map<String, String>> setter = Map::put;

  private final TextMapGetter<Map<String, String>> getter = new TextMapGetter<>() {
    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
      return carrier.keySet();
    }

    @Override
    public String get(Map<String, String> carrier, String key) {
      return carrier != null ? carrier.get(key) : null;
    }
  };

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Object getCurrentContext() {
    Map<String, String> carrier = new HashMap<>();
    propagator().inject(Context.current(), carrier, setter);
    return carrier;
  }

  @Override
  public void setCurrentContext(Object context) {
    @SuppressWarnings("unchecked")
    Map<String, String> carrier = (Map<String, String>) context;
    if (carrier == null || carrier.isEmpty()) {
      return;
    }
    propagator().extract(Context.current(), carrier, getter).makeCurrent();
  }

  @Override
  public Map<String, Payload> serializeContext(Object context) {
    @SuppressWarnings("unchecked")
    Map<String, String> carrier = (Map<String, String>) context;
    if (carrier == null || carrier.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Payload> serialized = new HashMap<>();
    DataConverter converter = DataConverter.getDefaultInstance();
    for (Map.Entry<String, String> entry : carrier.entrySet()) {
      converter.toPayload(entry.getValue())
              .ifPresent(payload -> serialized.put(entry.getKey(), payload));
    }
    return serialized;
  }

  @Override
  public Object deserializeContext(Map<String, Payload> header) {
    Map<String, String> carrier = new HashMap<>();
    DataConverter converter = DataConverter.getDefaultInstance();
    for (Map.Entry<String, Payload> entry : header.entrySet()) {
      String value = converter.fromPayload(entry.getValue(), String.class, String.class);
      carrier.put(entry.getKey(), value);
    }
    return carrier;
  }

  private @NonNull TextMapPropagator propagator() {
    return openTelemetry.getPropagators().getTextMapPropagator();
  }
}
