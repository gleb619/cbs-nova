package cbs.nova.temporal.workflow;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.Map;

@ActivityInterface
public interface GenericActivity {

  @ActivityMethod
  HelperOutput prepare(String helperCode, Map<String, Object> params);

  @ActivityMethod
  HelperOutput execute(String helperCode, HelperInput input);
}
