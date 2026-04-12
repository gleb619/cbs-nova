package cbs.nova.temporal.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface EventWorkflow {

  @WorkflowMethod
  WorkflowExecutionResult execute(EventWorkflowInput input);
}
