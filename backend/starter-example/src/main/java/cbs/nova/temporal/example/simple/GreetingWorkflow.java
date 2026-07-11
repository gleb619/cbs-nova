package cbs.nova.temporal.example.simple;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface GreetingWorkflow {

  @WorkflowMethod
  String greet(String name);
}
