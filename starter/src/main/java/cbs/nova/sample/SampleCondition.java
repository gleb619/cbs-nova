package cbs.nova.sample;

import cbs.dsl.api.ConditionFunction;
import cbs.dsl.api.ConditionTypes.ConditionInput;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslImplType;

/** Sample condition for the PoC. Always returns {@code true}. */
@DslComponent(code = "SAMPLE_CONDITION", type = DslImplType.CONDITION)
public class SampleCondition implements ConditionFunction<ConditionInput, ConditionOutput> {

  @Override
  public ConditionOutput evaluate(ConditionInput input) {
    return new ConditionOutput(true);
  }
}
