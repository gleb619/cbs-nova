package cbs.dsl.api;

import cbs.dsl.api.HelperFunction.HelperArg;
import cbs.dsl.api.HelperFunction.HelperResult;

@FunctionalInterface
public interface HelperFunction<I extends HelperArg, O extends HelperResult> {

  O execute(I input);

  interface HelperArg {}

  interface HelperResult {}
}
