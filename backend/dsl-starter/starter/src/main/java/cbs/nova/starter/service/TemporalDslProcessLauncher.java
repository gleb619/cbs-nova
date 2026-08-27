package cbs.nova.starter.service;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.process.DslTemporalProcess;
import cbs.nova.dsl.process.DslTemporalProcessFailure;
import cbs.nova.dsl.process.DslTemporalProcessRequest;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@RequiredArgsConstructor
public class TemporalDslProcessLauncher implements TemporalProcessLauncher {

  private final WorkflowClient workflowClient;
  private final ObjectMapper objectMapper;
  private final Duration executionTimeout;
  private final Duration taskTimeout;


  @Builder
  public record ProcessLaunchRequest(
          @NonNull String processName,
          @NonNull String taskQueue,
          @Nullable Class<?> inputType,
          @Nullable Class<?> outputType,
          @NonNull Context<?> ctx) {
  }

  @Override
  public boolean canRun(@NonNull Context<?> ctx) {
    if (ctx.mode() != ExecutionMode.RUN) {
      return false;
    }
    try {
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
    return launch(new ProcessLaunchRequest(processName, taskQueue, inputType, outputType, ctx));
  }

  public @NonNull Result<?> launch(@NonNull ProcessLaunchRequest request) {
    GeneratedClassDescriptor descriptor = GlobalManager.globalManager()
            .findGeneratedProcess(request.processName())
            .orElseThrow(() -> new IllegalArgumentException(
                    "No generated Temporal process: " + request.processName()));

    var options = WorkflowOptions.newBuilder()
            .setTaskQueue(request.taskQueue())
            .setWorkflowId(request.ctx().runId())
            .setWorkflowExecutionTimeout(executionTimeout)
            .setWorkflowTaskTimeout(taskTimeout)
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
            .build();

    var stub = workflowClient.newWorkflowStub(descriptor.temporalInterface(), options);
    try {
      if (!(stub instanceof DslTemporalProcess)) {
        return Result.failure(new DslExecutionException(request.ctx().runId(),
                "Stub is not a DslTemporalProcess: " + stub.getClass().getName(), null));
      }
      @SuppressWarnings("unchecked")
      DslTemporalProcess<Object> process = (DslTemporalProcess<Object>) stub;
      Object result = process.execute(
              new DslTemporalProcessRequest<>(request.ctx().runId(), request.ctx().body()));
      if (result instanceof DslTemporalProcessFailure(String message, String detail)) {
        return Result.failure(new DslExecutionException(request.ctx().runId(),
                message + ": " + detail,
                new RuntimeException(message)));
      }
      if (request.outputType() != null && result != null
              && !request.outputType().isInstance(result)) {
        result = objectMapper.convertValue(result, request.outputType());
      }
      return Result.success(result);
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      return Result.failure(new DslExecutionException(request.ctx().runId(),
              "Process %s failed: %s".formatted(request.processName(), cause.getMessage()), cause));
    }
  }
}
