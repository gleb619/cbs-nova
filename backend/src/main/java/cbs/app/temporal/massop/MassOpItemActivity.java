package cbs.app.temporal.massop;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface MassOpItemActivity {

  @ActivityMethod
  MassOpItemResult processItem(MassOpItemInput input);
}
