package cbs.nova.sample;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslImplType;
import cbs.dsl.api.HelperFunction;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;

import java.util.Map;

/** Sample helper for the PoC. Concatenates two string parameters. */
@DslComponent(code = "SAMPLE_HELPER", type = DslImplType.HELPER)
public class SampleHelper implements HelperFunction<HelperInput, HelperOutput> {

  @Override
  public HelperOutput execute(HelperInput input) {
    String first = input.params().getOrDefault("first", "").toString();
    String second = input.params().getOrDefault("second", "").toString();
    return new HelperOutput(Map.of("result", first + second));
  }
}
