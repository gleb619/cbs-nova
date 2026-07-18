package cbs.nova.starter.services;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.DslTemporalProcess;
import cbs.nova.dsl.DslTemporalProcessFailure;
import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TemporalProcessLauncher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Temporal implementation of {@link TemporalProcessLauncher}. It resolves the generated workflow
 * interface/implementation for a DSL process through
 * {@link GlobalManager#findGeneratedProcess(String)} and starts it on the process task queue. When
 * the caller is already inside a Temporal workflow thread, {@link #canRun(Context)} returns false
 * so the DSL runner falls back to executing the process logic directly and avoids recursive
 * workflow spawning.
 *
 * <p>
 * The launcher communicates with generated workflows through the {@link DslTemporalProcess}
 * contract instead of reflection.
 */
@RequiredArgsConstructor
public class TemporalDslProcessLauncher implements TemporalProcessLauncher {

  private static final Duration EXECUTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration TASK_TIMEOUT = Duration.ofSeconds(5);

  private final WorkflowClient workflowClient;

  @Override
  public boolean canRun(@NonNull Context<?> ctx) {
    if (ctx.mode() != ExecutionMode.RUN) {
      return false;
    }
    try {
      // If Workflow.getInfo() succeeds we are already inside a workflow thread;
      // let the in-workflow runner execute the DSL logic directly.
      Workflow.getInfo();
      return false;
    } catch (Throwable t) {
      return true;
    }
  }

  @Override
  public @NonNull Result<?> launch(
          @NonNull String processName,
          @NonNull String taskQueue,
          @Nullable Class<?> inputType,
          @Nullable Class<?> outputType,
          @NonNull Context<?> ctx) {
    GeneratedClassDescriptor descriptor = GlobalManager.globalManager()
            .findGeneratedProcess(processName)
            .orElseThrow(() -> new IllegalArgumentException(
                    "No generated Temporal process: " + processName));

    var options = WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .setWorkflowId(ctx.runId())
            .setWorkflowExecutionTimeout(EXECUTION_TIMEOUT)
            .setWorkflowTaskTimeout(TASK_TIMEOUT)
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
            .build();

    var stub = workflowClient.newWorkflowStub(descriptor.temporalInterface(), options);
    try {
      DslTemporalProcess process = (DslTemporalProcess) stub;
      Object result = process.execute(new DslTemporalProcessRequest<>(ctx.runId(), ctx.body()));
      if (result instanceof DslTemporalProcessFailure(String message, String detail)) {
        return Result.failure(new DslExecutionException(ctx.runId(),
            message + ": " + detail,
                new RuntimeException(message)));
      }
      if (outputType != null && result != null && !outputType.isInstance(result)) {
        result = new ObjectMapper().convertValue(result, outputType);
      }
      return Result.success(result);
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      return Result.failure(new DslExecutionException(ctx.runId(),
              "Process " + processName + " failed: " + cause.getMessage(), cause));
    }
  }
}
