package cbs.dsl.api;

import cbs.dsl.api.ConditionFunction.ConditionArg;
import cbs.dsl.api.ConditionFunction.ConditionResult;
import cbs.dsl.api.context.ConditionContext;
import java.util.Collections;
import java.util.Map;

@FunctionalInterface
public interface ConditionFunction<I extends ConditionArg, O extends ConditionResult> {

  ConditionContext<O> check(ConditionContext<I> input);

  interface ConditionArg extends DslPayload {}

  @FunctionalInterface
  interface ConditionResult extends DslPayload {

    boolean getValue();

    @Override
    default Map<String, Object> params() {
      return Collections.emptyMap();
    }

  }
}
