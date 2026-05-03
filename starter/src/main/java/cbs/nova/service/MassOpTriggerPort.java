package cbs.nova.service;

/**
 * Port interface for triggering mass operation workflows. Implemented in the backend module using
 * Temporal WorkflowClient.
 */
@FunctionalInterface
public interface MassOpTriggerPort {

  /**
   * Starts a mass operation workflow. Returns the Temporal workflow ID.
   *
   * @param massOpCode the mass operation code
   * @param performedBy the user who triggered the operation
   * @param dslVersion the DSL version to use
   * @param contextJson JSON context for the operation
   * @param workflowId the deterministic Temporal workflow ID
   * @return the Temporal workflow ID
   */
  String trigger(
      String massOpCode,
      String performedBy,
      String dslVersion,
      String contextJson,
      String workflowId);
}
