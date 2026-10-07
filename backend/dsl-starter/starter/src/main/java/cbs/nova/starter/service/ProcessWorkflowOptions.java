package cbs.nova.starter.service;

import cbs.nova.dsl.GeneratedClassDescriptor;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import java.time.Duration;
import org.jspecify.annotations.NonNull;

/**
 * Shared {@link WorkflowOptions} builder for DSL process workflows.
 *
 * <p>
 * Centralises the timeouts and retry policy so API-launched runs and schedule-fired runs behave
 * identically for the same definition.
 */
public final class ProcessWorkflowOptions {

  private ProcessWorkflowOptions() {
  }

  /**
   * Inputs required to build process-scoped {@link WorkflowOptions}.
   */
  public record Properties(
          @NonNull Duration executionTimeout,
          @NonNull Duration taskTimeout) {
  }

  /**
   * Returns a pre-configured builder with the task queue, execution timeout, task timeout and retry
   * policy already set. Callers can add workflow-id, memo and idempotency fields before building.
   */
  public static WorkflowOptions.Builder forProcess(
          @NonNull GeneratedClassDescriptor descriptor,
          @NonNull Properties properties) {
    return WorkflowOptions.newBuilder()
            .setTaskQueue(descriptor.taskQueue())
            .setWorkflowExecutionTimeout(properties.executionTimeout())
            .setWorkflowTaskTimeout(properties.taskTimeout())
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build());
  }
}
