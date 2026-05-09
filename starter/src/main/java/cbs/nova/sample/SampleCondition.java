package cbs.nova.sample;

import cbs.dsl.api.ConditionFunction;
import cbs.dsl.api.ConditionTypes.ConditionInput;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslComponent.DslImplType;

/** Sample condition for the PoC. Always returns {@code true}. */
@DslComponent(code = "SAMPLE_CONDITION", type = DslImplType.CONDITION)
public class SampleCondition implements ConditionFunction<ConditionInput, ConditionOutput> {

  @Override
  public ConditionOutput check(ConditionInput input) {
    //    return new ConditionContext<>(
    //        input.eventCode(), input.workflowExecutionId(), input.performedBy(),
    //        input.dslVersion(), input.eventParameters(), input.enrichment(),
    //        input.helperResolver(), input.isResumed(),
    //        new ConditionOutput(true));
    return null;
  }
}
