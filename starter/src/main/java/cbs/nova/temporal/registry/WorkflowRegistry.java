package cbs.nova.temporal.registry;

/**
 * Registry abstraction that maps logical workflow names to their Temporal runtime configuration.
 *
 * <p>Implementations must be thread-safe and immutable after construction. The wrapper classes rely
 * on this immutability for deterministic behaviour during workflow replay.
 */
// TODO: remove file
@Deprecated(forRemoval = true)
public interface WorkflowRegistry {

  /**
   * Looks up the Temporal configuration for a workflow by its logical name.
   *
   * @param logicalName the logical workflow name (e.g. "OrderWorkflow")
   * @return the workflow configuration
   * @throws IllegalArgumentException if no workflow is registered with the given name
   */
  WorkflowConfig getWorkflowConfig(String logicalName);
}
