package cbs.nova.starter.services;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslTemporalTransactionRequest;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GeneratedTransactionActivity;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.RetryPolicy;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.transaction.TransactionDslObject;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.jspecify.annotations.NonNull;

public final class TemporalTransactionInvoker implements TransactionInvoker {

  @Override
  public @NonNull Result<?> invoke(@NonNull String name, @NonNull Object input,
          @NonNull Context<?> ctx) {
    var txOpt = GlobalManager.globalManager().findTransaction(name);
    var generatedOpt = GlobalManager.globalManager().findGeneratedTransaction(name);
    if (txOpt.isEmpty() || generatedOpt.isEmpty()) {
      // TODO: add log here
      return GlobalManager.globalManager().runTransaction(name, ctx);
    }

    GeneratedClassDescriptor descriptor = generatedOpt.get();
    TransactionDslObject tx = txOpt.get();
    ActivityOptions options = buildActivityOptions(tx);
    Object stub = Workflow.newActivityStub(descriptor.temporalInterface(), options);
    try {
      var request = new DslTemporalTransactionRequest<>(ctx.runId(), input);
      if (stub instanceof GeneratedTransactionActivity activity) {
        Object value = activity.execute(request);
        return Result.success(value);
      }

      return Result.failure(new IllegalArgumentException("Can't execute activity code"));
    } catch (Throwable t) {
      Throwable cause = t.getCause() != null ? t.getCause() : t;
      return Result.failure(cause);
    }
  }

  // TODO: in dsl object we have similar firlds for retry, task queue, etc, e.g. we need to reuse it
  // here too
  private ActivityOptions buildActivityOptions(TransactionDslObject tx) {
    RetryPolicy policy = tx.retryPolicy();
    if (policy == null) {
      policy = DslConfig.dslConfig().defaultRetryPolicy();
    }
    RetryOptions retryOptions = RetryOptions.newBuilder()
            .setMaximumAttempts(policy.maxAttempts())
            .setInitialInterval(policy.initialInterval())
            .setBackoffCoefficient(policy.backoffCoefficient())
            .build();
    return ActivityOptions.newBuilder()
            .setStartToCloseTimeout(tx.startToCloseTimeout())
            .setRetryOptions(retryOptions)
            .setTaskQueue(tx.taskQueue())
            .build();
  }
}
