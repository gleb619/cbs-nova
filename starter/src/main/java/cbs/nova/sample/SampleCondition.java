package cbs.nova.sample;

import cbs.dsl.api.ConditionFunction;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslImplType;
import cbs.nova.sample.SampleCondition.SampleConditionInput;
import io.avaje.jsonb.Json;
import java.util.Map;

/** Sample condition for the PoC. Always returns {@code true}. */
@DslComponent(code = "SAMPLE_CONDITION", type = DslImplType.CONDITION)
public class SampleCondition
    implements ConditionFunction<SampleConditionInput, ConditionOutput> {

  @Override
  public ConditionOutput evaluate(SampleConditionInput input) {
    return new ConditionOutput(true);
  }

  @Json
  public record SampleConditionInput(String name) implements ConditionArg {

    @Override
    public Map<String, Object> toMap() {
      return Map.of("name", name);
    }
  }

}
