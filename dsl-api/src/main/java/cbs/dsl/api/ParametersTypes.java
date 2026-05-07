package cbs.dsl.api;

import cbs.dsl.api.ParametersFunction.ParametersArg;
import cbs.dsl.api.ParametersFunction.ParametersResult;
import io.avaje.jsonb.Json;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

/** Consolidated Parameters DSL types. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ParametersTypes {

  @Json
  @Builder(toBuilder = true)
  public record ParametersInput(Map<String, Object> params)
      implements ParametersArg {

    public ParametersInput from(Map<String, Object> input) {
      return new ParametersInput(input);
    }

    public ParametersOutput asOutput() {
      return new ParametersOutput(params);
    }
  }

  @Json
  @Builder(toBuilder = true)
  public record ParametersOutput(Map<String, Object> params) implements ParametersResult {

  }

}
