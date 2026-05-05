package cbs.dsl.api;

import cbs.dsl.api.HelperFunction.HelperArg;
import cbs.dsl.api.HelperFunction.HelperResult;
import cbs.dsl.api.context.HelperContext;

@FunctionalInterface
public interface HelperFunction<I extends HelperArg, O extends HelperResult> {

  HelperContext<O> execute(HelperContext<I> input);

  default HelperContext<O> preview(HelperContext<I> input) {
    return execute(input);
  }

  interface HelperArg extends DslPayload {}

  interface HelperResult extends DslPayload {}
}
