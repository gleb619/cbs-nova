package cbs.nova.entity;

/**
 * Represents the status of an individual item within a mass operation.
 */
public enum MassOperationItemStatus {
  /**
   * Item is waiting to be processed.
   */
  PENDING,
  /**
   * Item is currently being processed.
   */
  RUNNING,
  /**
   * Item has been processed successfully.
   */
  DONE,
  /**
   * Item processing has failed.
   */
  FAILED
}
