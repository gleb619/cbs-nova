package cbs.dsl.api;

import cbs.dsl.api.ParametersFunction.ParametersArg;
import cbs.dsl.api.ParametersFunction.ParametersResult;

@FunctionalInterface
public interface ParametersFunction<I extends ParametersArg, O extends ParametersResult> {

  O execute(I input);

  interface ParametersArg extends DslPayload {}

  interface ParametersResult extends DslPayload {}
}
