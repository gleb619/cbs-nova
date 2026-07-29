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
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@RequiredArgsConstructor
public class TemporalDslProcessLauncher implements TemporalProcessLauncher {

  // TODO: use app.yml instead
  @Deprecated(forRemoval = true)
  private static final Duration EXECUTION_TIMEOUT = Duration.ofSeconds(30);
  @Deprecated(forRemoval = true)
  private static final Duration TASK_TIMEOUT = Duration.ofSeconds(5);

  private final WorkflowClient workflowClient;
  private final ObjectMapper objectMapper;

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
  // TODO: Simplify method, introduce a parameter object(record), that have 5 fields + lombok
  // builder
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

    // TODO: in dsl we configured some of next fields(like task queue, timeouts, retry), we need to
    // use them here(we can configure a codegenerator for descriptor if needeed)
    var options = WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .setWorkflowId(ctx.runId())
            .setWorkflowExecutionTimeout(EXECUTION_TIMEOUT)
            .setWorkflowTaskTimeout(TASK_TIMEOUT)
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
            .build();

    var stub = workflowClient.newWorkflowStub(descriptor.temporalInterface(), options);
    try {
      // TODO: instead of blind cast, use if with instanceof here, or else throw ex
      var process = (DslTemporalProcess) stub;
      Object result = process.execute(new DslTemporalProcessRequest<>(ctx.runId(), ctx.body()));
      if (result instanceof DslTemporalProcessFailure(String message, String detail)) {
        return Result.failure(new DslExecutionException(ctx.runId(),
                message + ": " + detail,
                new RuntimeException(message)));
      }
      if (outputType != null && result != null && !outputType.isInstance(result)) {
        // TODO: Modify one of codegenerated code, we need a special class, that know all records
        // from a `models` folder, and can use avaje jsonb converter, and fallback to jackson
        result = objectMapper.convertValue(result, outputType);
      }
      return Result.success(result);
      // TODO: create a new DslException at `dsl-api`, make project exceptions implement it(e.g.
      // search usage of NullPointer, IllegalStatement exceptions and replace with a new ones)
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      return Result.failure(new DslExecutionException(ctx.runId(),
              "Process %s failed: %s".formatted(processName, cause.getMessage()), cause));
    }
  }
}
