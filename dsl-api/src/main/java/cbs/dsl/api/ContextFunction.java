package cbs.dsl.api;

import cbs.dsl.api.ContextFunction.ContextArg;
import cbs.dsl.api.ContextFunction.ContextResult;

@FunctionalInterface
public interface ContextFunction<I extends ContextArg, O extends ContextResult> {

  O execute(I input);

  interface ContextArg extends DslPayload {}

  interface ContextResult extends DslPayload {}
}
