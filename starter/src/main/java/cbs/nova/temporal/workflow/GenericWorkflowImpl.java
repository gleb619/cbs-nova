package cbs.nova.temporal.workflow;

import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.nova.temporal.ActivityManager;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GenericWorkflowImpl implements GenericWorkflow {

  private final ActivityManager activityManager;

  public GenericWorkflowImpl(ActivityManager activityManager) {
    this.activityManager = activityManager;
  }

  @Override
  public TransactionOutput execute(GenericTransactionRequest request) {
    try {
      Class<?> iface = activityManager.getActivityInterface(request.activityCode());

      @SuppressWarnings("unchecked")
      Object stub =
          Workflow.newActivityStub(
              iface,
              ActivityOptions.newBuilder()
                  .setStartToCloseTimeout(Duration.ofSeconds(30))
                  .setRetryOptions(
                      RetryOptions.newBuilder()
                          .setMaximumAttempts(5)
                          .build())
                  .build());

      log.debug("Calling prepare on activity: {}", request.activityCode());
      Method prepare = iface.getMethod("prepare", Map.class);
      prepare.invoke(stub, request.input().params());

      log.debug("Calling execute on activity: {}", request.activityCode());
      Method execute = iface.getMethod("execute", TransactionInput.class);
      return (TransactionOutput) execute.invoke(stub, request.input());

    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ActivityFailure) {
        log.warn("Activity failed, calling rollback: {}", request.activityCode());
        try {
          Class<?> iface = activityManager.getActivityInterface(request.activityCode());
          @SuppressWarnings("unchecked")
          Object stub =
              Workflow.newActivityStub(
                  iface,
                  ActivityOptions.newBuilder()
                      .setStartToCloseTimeout(Duration.ofSeconds(30))
                      .build());

          Method rollback = iface.getMethod("rollback", TransactionInput.class);
          return (TransactionOutput) rollback.invoke(stub, request.input());
        } catch (Exception rollbackEx) {
          throw ApplicationFailure.newNonRetryableFailure(
              rollbackEx.getMessage(), rollbackEx.getClass().getName());
        }
      }
      throw ApplicationFailure.newNonRetryableFailure(
          e.getMessage(), e.getClass().getName());
    } catch (Exception e) {
      throw ApplicationFailure.newNonRetryableFailure(e.getMessage(), e.getClass().getName());
    }
  }
}