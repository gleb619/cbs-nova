package cbs.dsl.api;

import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consolidated condition DSL types.
 *
 * <p>ConditionInput carries the raw parameters and event metadata for evaluating a condition.
 * ConditionOutput wraps the boolean result of the evaluation.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConditionTypes {

  /** ConditionInput carries the raw parameters and event metadata for evaluating a condition. */
  @Json
  @Builder(toBuilder = true)
  public record ConditionInput(Map<String, Object> params, String eventCode, Long eventNumber)
      implements ConditionFunction.ConditionArg {

  }

  /** ConditionOutput wraps the boolean result of the evaluation. */
  @Json
  @Builder(toBuilder = true)
  public record ConditionOutput(boolean result) implements ConditionFunction.ConditionResult {
    @Override
    public boolean getValue() {
      return result;
    }
  }
}
