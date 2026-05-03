package cbs.nova.service;

import cbs.nova.model.EventWorkflowRequest;
import cbs.nova.model.WorkflowExecutionResponse;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface EventWorkflow {

  @WorkflowMethod
  WorkflowExecutionResponse execute(EventWorkflowRequest input);
}
