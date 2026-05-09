package cbs.dsl.api;

import cbs.dsl.api.ContextFunction.ContextArg;
import cbs.dsl.api.ContextFunction.ContextResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Consolidated Context DSL types. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ContextTypes {

  @Json
  @Builder(toBuilder = true)
  public record ContextInput(Map<String, Object> params) implements ContextArg {

    public static ContextInput from(Map<String, Object> input) {
      return new ContextInput(input);
    }

    public ContextOutput asOutput() {
      return new ContextOutput(params);
    }
  }

  @Json
  @Builder(toBuilder = true)
  public record ContextOutput(Map<String, Object> params) implements ContextResult {

    public static ContextOutput from(Map<String, Object> params) {
      return new ContextOutput(params);
    }
  }
}
