package cbs.dsl.api;

import cbs.dsl.api.ConditionFunction.ConditionArg;
import cbs.dsl.api.ConditionFunction.ConditionResult;

@FunctionalInterface
public interface ConditionFunction<I extends ConditionArg, O extends ConditionResult> {

  O evaluate(I input);

  interface ConditionArg {}

  @FunctionalInterface
  interface ConditionResult {

    boolean getValue();
  }
}
