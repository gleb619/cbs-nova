package cbs.nova.dsl.config;

import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.RetryPolicy;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.runner.HelperRunner;
import cbs.nova.dsl.transaction.TransactionRunner;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public class DslConfig implements SingletonSupport {

  private final Scope scope;

  public static DslConfig dslConfig() {
    return Holder.INSTANCE.dslConfig();
  }

  public static DslConfig dslConfig(Scope scope) {
    return Holder.INSTANCE.dslConfig(scope);
  }

  /* ============= */

  public @NonNull RetryPolicy defaultRetryPolicy() {
    return singleton(() -> new RetryPolicy(3, Duration.ofSeconds(1), 2.0));
  }

  public @NonNull ExecutionTraceCollector executionTraceCollector() {
    return singleton(ExecutionTraceCollector::new);
  }

  public @NonNull ContextFactory contextFactory() {
    return singleton(ContextFactory::new);
  }

  public @NonNull RetryPolicyFactory retryPolicyFactory() {
    return singleton(RetryPolicyFactory::new);
  }

  public @NonNull ProcessRunner processRunner(
          @NonNull ExecutionTraceCollector executionTraceCollector,
          @NonNull ContextFactory contextFactory) {
    return singleton(() -> new DefaultProcessRunner(executionTraceCollector, contextFactory));
  }

  public @NonNull TransactionRunner transactionRunner(
          @NonNull ExecutionTraceCollector executionTraceCollector,
          @NonNull ContextFactory contextFactory) {
    return singleton(() -> new DefaultTransactionRunner(executionTraceCollector, contextFactory));
  }

  public @NonNull HelperRunner helperRunner(
          @NonNull ExecutionTraceCollector executionTraceCollector,
          @NonNull ContextFactory contextFactory) {
    return singleton(() -> new DefaultHelperRunner(executionTraceCollector, contextFactory));
  }

  /* ============= */

  @Getter
  private static final class Holder implements SingletonSupport {

    public static final Holder INSTANCE = new Holder();

    private final SingletonScope scope = SingletonScope.of();

    public DslConfig dslConfig() {
      return dslConfig(SingletonScope.of());
    }

    public DslConfig dslConfig(Scope scope) {
      return singleton(scope.id(), () -> new DslConfig(scope));
    }
  }

}
