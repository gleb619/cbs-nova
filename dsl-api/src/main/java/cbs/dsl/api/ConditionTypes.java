package cbs.dsl.api;

import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConditionTypes {

  @Json
  @Builder(toBuilder = true)
  public record ConditionInput(Map<String, Object> params, String eventNumber)
      implements ConditionFunction.ConditionArg {

    public static ConditionInput from(Map<String, Object> params) {
      return ConditionInput.builder()
          .params(params)
          .build();
    }
  }

  @Json
  @Builder(toBuilder = true)
  public record ConditionOutput(boolean result) implements ConditionFunction.ConditionResult {
    @Override
    public boolean getValue() {
      return result;
    }
  }
}
