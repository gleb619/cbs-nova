package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslComponent.DslImplType;
import cbs.dsl.api.HelperFunction;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.context.HelperContext;
import io.avaje.jsonb.Json;

import java.util.Map;

/**
 * Sample helper for the PoC. Returns a simple greeting.
 */
@DslComponent(code = "SAMPLE_HELPER", type = DslImplType.HELPER)
public class SampleHelper implements HelperFunction<HelperInput, HelperOutput> {

  @Override
  public HelperContext<HelperOutput> execute(HelperContext<HelperInput> input) {
    String name = (String) input.payload().params().getOrDefault("name", "World");
    return input.toBuilder().payload(new HelperOutput(Map.of("greeting", "Hello, " + name + "!"))).build();
  }

  @Json
  public record SampleHelperInput(String name) implements HelperArg {

    @Override
    public Map<String, Object> params() {
      return Map.of("name", name);
    }
  }
}
