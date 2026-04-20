package cbs.app.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface TransactionActivity {

  @ActivityMethod
  TransactionResult executeTransaction(TransactionActivityInput input);
}
