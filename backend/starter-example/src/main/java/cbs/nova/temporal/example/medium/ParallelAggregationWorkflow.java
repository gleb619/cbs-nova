package cbs.nova.temporal.example.medium;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ParallelAggregationWorkflow {

  @WorkflowMethod
  String aggregate(String input);
}
