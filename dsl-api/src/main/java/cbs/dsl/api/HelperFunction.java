package cbs.dsl.api;

import cbs.dsl.api.HelperFunction.HelperArg;
import cbs.dsl.api.HelperFunction.HelperResult;

import java.util.Map;

@FunctionalInterface
public interface HelperFunction<I extends HelperArg, O extends HelperResult> {

  O execute(I input);

  default O preview(I input) {
    return execute(input);
  }

  interface HelperArg extends DslPayload {

    @Override
    default Map<String, Object> params() {
      return JsonPayload.toMap(this);
    }
  }

  interface HelperResult extends DslPayload {

    @Override
    default Map<String, Object> params() {
      return JsonPayload.toMap(this);
    }
  }
}
