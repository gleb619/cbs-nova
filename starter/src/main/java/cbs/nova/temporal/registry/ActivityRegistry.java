package cbs.nova.temporal.registry;

/**
 * Registry abstraction that maps logical activity names to their Temporal runtime configuration.
 *
 * <p>Implementations must be thread-safe and immutable after construction. The
 * {@link cbs.nova.temporal.ActivityStubManager} reads from this registry inside workflows; because
 * the registry is static and immutable, the lookup is deterministic and replay-safe.
 */
public interface ActivityRegistry {

  /**
   * Looks up the Temporal configuration for an activity by its logical name.
   *
   * @param logicalName the logical activity name (e.g. "ProcessInput")
   * @return the activity configuration
   * @throws IllegalArgumentException if no activity is registered with the given name
   */
  ActivityConfig getActivityConfig(String logicalName);
}
