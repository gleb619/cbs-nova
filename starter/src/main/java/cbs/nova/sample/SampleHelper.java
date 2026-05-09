package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslComponent.DslImplType;
import cbs.dsl.api.HelperFunction;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.nova.sample.SampleHelper.SampleHelperInput;
import cbs.nova.sample.SampleHelper.SampleHelperOutput;
import io.avaje.jsonb.Json;

import java.util.Map;

/** Sample helper for the PoC. Returns a simple name. */
@DslComponent(code = "SAMPLE_HELPER", type = DslImplType.HELPER)
public class SampleHelper implements HelperFunction<SampleHelperInput, SampleHelperOutput> {

  @Override
  public SampleHelperOutput execute(SampleHelperInput input) {
    return new SampleHelperOutput("Hello, " + input.name());
  }

  @Json
  public record SampleHelperInput(String name) implements HelperArg {

  }

  @Json
  public record SampleHelperOutput(String name) implements HelperResult {

  }
}
