package cbs.nova.service;

/**
 * Port interface for sending signals to running MassOpWorkflow instances. Implemented in the
 * backend module using Temporal WorkflowClient.
 */
@FunctionalInterface
public interface MassOpSignalPort {

  /**
   * Sends a signal to a running MassOpWorkflow.
   *
   * @param temporalWorkflowId the Temporal workflow ID
   * @param signalType the signal type: "LOCK" or "UNLOCK"
   */
  void signal(String temporalWorkflowId, String signalType);
}
