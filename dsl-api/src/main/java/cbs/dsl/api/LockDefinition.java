package cbs.dsl.api;

import cbs.dsl.api.context.MassOperationContext;

/**
 * Defines a lock predicate for mass operations — determines whether a given item should be skipped.
 */
public interface LockDefinition {

  /**
   * Returns {@code true} if the item described by {@code ctx} is locked and should be skipped.
   *
   * @param ctx the mass operation context
   * @return {@code true} if locked
   */
  boolean isLocked(MassOperationContext ctx);
}
