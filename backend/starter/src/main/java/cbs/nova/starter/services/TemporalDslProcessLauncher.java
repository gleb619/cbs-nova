package cbs.nova.starter.services;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TemporalProcessLauncher;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.workflow.Workflow;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    GeneratedClassDescriptor descriptor = GlobalManager.getInstance()
            .findGeneratedProcess(processName)
            .orElseThrow(() -> new IllegalArgumentException(
                    "No generated Temporal process: " + processName));

    var options = WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .setWorkflowId(ctx.runId())
            .setWorkflowExecutionTimeout(EXECUTION_TIMEOUT)
            .setWorkflowTaskTimeout(TASK_TIMEOUT)
            .build();

    var stub = workflowClient.newWorkflowStub(descriptor.temporalInterface(), options);
    try {
      Method runMethod = findRunMethod(descriptor.temporalInterface(), descriptor.inputType());
      Object result = runMethod.invoke(stub, ctx.body());
      return Result.success(result);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      return Result.failure(new DslExecutionException(ctx.runId(),
              "Process " + processName + " failed: " + cause.getMessage(), cause));
    } catch (ReflectiveOperationException e) {
      return Result.failure(new DslExecutionException(ctx.runId(),
              "Failed to execute process " + processName, e));
    }
  }

  private Method findRunMethod(Class<?> workflowInterface, Class<?> inputType) {
    for (Method method : workflowInterface.getMethods()) {
      if ("run".equals(method.getName()) && method.getParameterCount() == 1) {
        if (inputType == null || method.getParameterTypes()[0].isAssignableFrom(inputType)) {
          return method;
        }
      }
    }
    throw new IllegalArgumentException("No suitable run method on " + workflowInterface.getName());
  }
}
