package cbs.dsl.api;

import java.util.Map;

public record Signal(
  String source,
  SignalType type,
  Map<String, Object> payload
) {
  public static Signal partial(String source, Map<String, Object> payload) {
    return new Signal(source, SignalType.PARTIAL, payload);
  }

  public static Signal completed(String source, Map<String, Object> payload) {
    return new Signal(source, SignalType.COMPLETED, payload);
  }
}
