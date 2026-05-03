package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslImplType;
import cbs.dsl.api.HelperFunction;
import cbs.nova.sample.SampleHelper.SampleHelperInput;
import cbs.nova.sample.SampleHelper.SampleHelperOutput;
import io.avaje.jsonb.Json;

import java.util.Map;

/** Sample helper for the PoC. Concatenates two string parameters. */
@DslComponent(code = "SAMPLE_HELPER", type = DslImplType.HELPER)
public class SampleHelper implements HelperFunction<SampleHelperInput, SampleHelperOutput> {

  @Override
  public SampleHelperOutput execute(SampleHelperInput input) {
    return new SampleHelperOutput(input.someVal() + "!");
  }

  @Json
  public record SampleHelperInput(String someVal) implements HelperArg {

    @Override
    public Map<String, Object> toMap() {
      return Map.of("someVal", someVal);
    }
  }

  @Json
  public record SampleHelperOutput(String result) implements HelperResult {

    @Override
    public Map<String, Object> toMap() {
      return Map.of("result", result);
    }
  }
}
