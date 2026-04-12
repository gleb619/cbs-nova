package cbs.nova.entity;

/**
 * Represents the lifecycle status of a mass operation execution.
 */
public enum MassOperationStatus {
  /** Mass operation is currently running. */
  RUNNING,
  /** Mass operation has completed successfully for all items. */
  DONE,
  /** Mass operation has completed but some items failed. */
  DONE_WITH_FAILURES,
  /** Mass operation is locked and cannot be modified. */
  LOCKED,
  /** Mass operation has encountered a fatal error. */
  FAULTED
}
