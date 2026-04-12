package cbs.app.temporal.massop;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface MassOpWorkflow {

  @WorkflowMethod
  MassOpResult execute(MassOpInput input);

  @SignalMethod
  void signal(MassOpSignal signal);
}
