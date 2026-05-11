package cbs.dsl.api;

import cbs.dsl.api.HelperFunction.HelperArg;
import cbs.dsl.api.HelperFunction.HelperResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Consolidated helper DSL types. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HelperTypes {

  @Json
  @Builder(toBuilder = true)
  public record HelperInput(Map<String, Object> params, String eventNumber) implements HelperArg {

    public static HelperInput from(Map<String, Object> params) {
      return HelperInput.builder()
          .params(params)
          .eventNumber("Insert value in HelperTypes#from")
          .build();
    }
  }

  @Json
  @Builder(toBuilder = true)
  public record HelperOutput(Map<String, Object> params) implements HelperResult {}
}
