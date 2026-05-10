package cbs.nova.temporal.workflow;

import cbs.dsl.api.TransactionTypes.TransactionOutput;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface GenericWorkflow {

  @WorkflowMethod(name = "GENERIC_WORKFLOW")
  TransactionOutput execute(GenericTransactionRequest request);
}