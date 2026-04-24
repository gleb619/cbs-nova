package cbs.dsl.api;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Consolidated signal types for mass operation completion signals. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SignalTypes {

  @Builder
  public record Signal(String source, SignalType type, Map<String, Object> payload) {
    public static Signal partial(String source, Map<String, Object> payload) {
      return new Signal(source, SignalType.PARTIAL, payload);
    }

    public static Signal completed(String source, Map<String, Object> payload) {
      return new Signal(source, SignalType.COMPLETED, payload);
    }
  }

  /** Indicates a partial completion, requiring more iterations. */
  public enum SignalType {
    /** Indicates a partial completion, requiring more iterations. */
    PARTIAL,
    /** Indicates full completion of the mass operation. */
    COMPLETED,
  }
}
