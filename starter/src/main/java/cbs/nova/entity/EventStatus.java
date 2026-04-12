package cbs.nova.entity;

/**
 * Represents the execution status of an event.
 */
public enum EventStatus {
  /** Event is currently executing. */
  RUNNING,
  /** Event has completed successfully. */
  COMPLETED,
  /** Event has encountered an error. */
  FAULTED
}
