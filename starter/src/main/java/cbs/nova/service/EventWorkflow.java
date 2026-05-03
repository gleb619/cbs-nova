package cbs.nova.service;

import cbs.nova.model.EventWorkflowInput;
import cbs.nova.model.WorkflowExecutionResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface EventWorkflow {

  @WorkflowMethod
  WorkflowExecutionResult execute(EventWorkflowInput input);
}
