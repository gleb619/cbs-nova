package cbs.nova.starter.service;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.idempotency.IdempotencyKeyReusedException;
import cbs.nova.starter.idempotency.IdempotentReplayException;
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
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.workflow.Workflow;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

@RequiredArgsConstructor
public class TemporalDslProcessLauncher implements TemporalProcessLauncher {

  private final WorkflowClient workflowClient;
  private final ObjectMapper objectMapper;
  private final Duration executionTimeout;
  private final Duration taskTimeout;
  private final WorkflowIdReusePolicy idempotencyReusePolicy;

  public TemporalDslProcessLauncher(WorkflowClient workflowClient, ObjectMapper objectMapper,
          Duration executionTimeout, Duration taskTimeout) {
    this(workflowClient, objectMapper, executionTimeout, taskTimeout,
            WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE);
  }

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

    String idempotencyFingerprint = idempotencyFingerprint(request.ctx());
    var optionsBuilder = ProcessWorkflowOptions.forProcess(descriptor,
            new ProcessWorkflowOptions.Properties(executionTimeout, taskTimeout))
            .setWorkflowId(request.ctx().runId());
    if (idempotencyFingerprint != null) {
      optionsBuilder
              .setWorkflowIdReusePolicy(idempotencyReusePolicy)
              .setMemo(Map.of(StarterConstants.IDEMPOTENCY_FINGERPRINT_METADATA_KEY,
                      idempotencyFingerprint));
    }
    var options = optionsBuilder.build();

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
      if (e instanceof WorkflowExecutionAlreadyStarted
              || cause instanceof WorkflowExecutionAlreadyStarted) {
        String runId = request.ctx().runId();
        if (idempotencyFingerprint != null
                && !fingerprintMatchesStored(runId, idempotencyFingerprint)) {
          return Result.failure(new IdempotencyKeyReusedException(runId));
        }
        return Result.failure(new IdempotentReplayException(runId));
      }
      return Result.failure(new DslExecutionException(request.ctx().runId(),
              "Process %s failed: %s".formatted(request.processName(), cause.getMessage()), cause));
    }
  }

  private @Nullable String idempotencyFingerprint(@NonNull Context<?> ctx) {
    Object value = ctx.metadata().get(StarterConstants.IDEMPOTENCY_FINGERPRINT_METADATA_KEY);
    return value instanceof String s ? s : null;
  }

  private boolean fingerprintMatchesStored(@NonNull String runId, @NonNull String expected) {
    try {
      WorkflowStub stub = workflowClient.newUntypedWorkflowStub(runId);
      WorkflowExecutionDescription description = stub.describe();
      String stored = description.getMemo(StarterConstants.IDEMPOTENCY_FINGERPRINT_METADATA_KEY,
              String.class, String.class);
      return stored != null && expected.equals(stored);
    } catch (RuntimeException ex) {
      return false;
    }
  }
}
