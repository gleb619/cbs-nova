package cbs.nova.starter.services;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.GeneratedClassDescriptor;
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TransactionInvoker implementation that routes DSL transactions to generated Temporal activity
 * stubs.
 *
 * <p>
 * Instead of using {@link java.lang.reflect.Method} inside generated workflow code, this bean
 * resolves a {@link java.lang.invoke.MethodHandle} for the typed {@code execute} method of the
 * generated activity interface and invokes it on the Temporal stub.
 * </p>
 */
public final class TemporalTransactionInvoker implements TransactionInvoker {

  private final Map<String, Caller> callers = new ConcurrentHashMap<>();

  private record Caller(Class<?> iface, MethodHandle execute, ActivityOptions options) {
  }

  @Override
  public @NonNull Result<?> invoke(@NonNull String name, @NonNull Object input,
          @NonNull Context<?> ctx) {
    var txOpt = GlobalManager.getInstance().findTransaction(name);
    var generatedOpt = GlobalManager.getInstance().findGeneratedTransaction(name);
    if (txOpt.isEmpty() || generatedOpt.isEmpty()) {
      return GlobalManager.getInstance().runTransaction(name, ctx);
    }

    GeneratedClassDescriptor descriptor = generatedOpt.get();
    TransactionDslObject tx = txOpt.get();
    Caller caller = callers.computeIfAbsent(name, n -> buildCaller(descriptor, tx));
    Object stub = Workflow.newActivityStub(caller.iface, caller.options);
    try {
      Object value = caller.execute.invoke(stub, input);
      return Result.success(value);
    } catch (Throwable t) {
      Throwable cause = t.getCause() != null ? t.getCause() : t;
      return Result.failure(cause);
    }
  }

  private Caller buildCaller(GeneratedClassDescriptor descriptor, TransactionDslObject tx) {
    Class<?> iface = descriptor.temporalInterface();
    Class<?> inputType = descriptor.inputType() != null ? descriptor.inputType() : Object.class;
    MethodHandle handle;
    try {
      handle = MethodHandles.publicLookup()
              .findVirtual(iface, "execute", MethodType.methodType(Object.class, inputType));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(
              "Generated transaction activity " + descriptor.name() + " has no execute method", e);
    }
    return new Caller(iface, handle, buildActivityOptions(tx));
  }

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
