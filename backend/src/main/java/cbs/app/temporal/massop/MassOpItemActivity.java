package cbs.app.temporal.massop;

import cbs.nova.entity.MassOperationExecutionEntity;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface MassOpItemActivity {

  @ActivityMethod
  MassOpItemResult processItem(MassOpItemInput input);

  @ActivityMethod
  void persistItem(MassOpItemPersistInput input);

  @ActivityMethod
  void updateCounts(MassOpCountsUpdateInput input);

  @ActivityMethod
  MassOperationExecutionEntity createLockedExecution(MassOpExecutionCreateInput input);

  @ActivityMethod
  MassOperationExecutionEntity createRunningExecution(MassOpExecutionCreateInput input);
}
