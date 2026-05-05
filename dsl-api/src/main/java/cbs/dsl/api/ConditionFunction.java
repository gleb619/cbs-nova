package cbs.dsl.api;

import cbs.dsl.api.ConditionFunction.ConditionArg;
import cbs.dsl.api.ConditionFunction.ConditionResult;
import cbs.dsl.api.context.ConditionContext;

@FunctionalInterface
public interface ConditionFunction<I extends ConditionArg, O extends ConditionResult> {

  ConditionContext<O> evaluate(ConditionContext<I> input);

  interface ConditionArg extends DslPayload {}

  @FunctionalInterface
  interface ConditionResult {

    boolean getValue();
  }
}
