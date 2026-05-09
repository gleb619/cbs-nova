package cbs.nova.temporal.registry;

import java.time.Duration;

/**
 * Configuration record for a Temporal workflow, resolved from a {@link WorkflowRegistry}.
 *
 * <p>All values are immutable and deterministic, making this safe to use inside replaying workflows
 * (via the client-side wrapper) and at worker initialisation time.
 *
 * @param logicalName the logical name used to look up this workflow (e.g. "OrderWorkflow")
 * @param workflowInterface the workflow interface class
 * @param taskQueue the Temporal task queue this workflow listens on
 * @param workflowExecutionTimeout max execution time for the entire workflow
 * @param workflowRunTimeout max run time for a single workflow attempt
 * @param defaultWorkflowIdPrefix optional prefix for auto-generated workflow IDs
 */
// TODO: remove file
@Deprecated(forRemoval = true)
public record WorkflowConfig(
    String logicalName,
    Class<?> workflowInterface,
    String taskQueue,
    Duration workflowExecutionTimeout,
    Duration workflowRunTimeout,
    String defaultWorkflowIdPrefix) {}
