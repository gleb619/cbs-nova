package cbs.nova.entity;

/**
 * Represents the lifecycle status of a workflow execution.
 */
public enum WorkflowStatus {
  /** Workflow is currently running. */
  ACTIVE,
  /** Workflow has completed successfully. */
  CLOSED,
  /** Workflow has encountered an unrecoverable error. */
  FAULTED
}
