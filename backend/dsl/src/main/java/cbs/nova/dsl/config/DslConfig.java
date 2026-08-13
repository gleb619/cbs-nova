package cbs.nova.dsl.config;

import cbs.nova.dsl.CompensationRegistry;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.HelperInstanceResolver;
import cbs.nova.dsl.HelperManager;
import cbs.nova.dsl.RetryPolicy;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.process.ProcessManager;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.registry.DefaultProcessRegistry;
import cbs.nova.dsl.registry.DefaultTransactionRegistry;
import cbs.nova.dsl.registry.GeneratedClassRegistry;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.runner.HelperRunner;
import cbs.nova.dsl.transaction.TransactionManager;
import cbs.nova.dsl.transaction.TransactionRunner;
import cbs.nova.dsl.utils.ExpressionEvaluator;
import cbs.nova.dsl.utils.SimpleExpressionEvaluator;
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

  public @NonNull ContextFactory contextFactory() {
    return singleton(ContextFactory::new);
  }

  public @NonNull RetryPolicyFactory retryPolicyFactory() {
    return singleton(RetryPolicyFactory::new);
  }

  public @NonNull Replaceable<TransactionInvoker> transactionInvoker() {
    return replaceable("transactionInvoker");
  }

  public @NonNull Replaceable<ExpressionEvaluator> expressionEvaluator() {
    return replaceable(SimpleExpressionEvaluator::new);
  }

  public @NonNull Replaceable<TemporalProcessLauncher> temporalProcessLauncher() {
    return replaceable("temporalProcessLauncher");
  }

  public @NonNull ProcessRunner processRunner(
          @NonNull ContextFactory contextFactory,
          @NonNull CompensationRegistry compensationRegistry) {
    return singleton(() -> new DefaultProcessRunner(contextFactory, compensationRegistry));
  }

  public @NonNull TransactionRunner transactionRunner(
          @NonNull ContextFactory contextFactory,
          @NonNull CompensationRegistry compensationRegistry) {
    return singleton(() -> new DefaultTransactionRunner(contextFactory, compensationRegistry));
  }

  public @NonNull HelperRunner helperRunner(@NonNull ContextFactory contextFactory) {
    return singleton(() -> new DefaultHelperRunner(contextFactory));
  }

  public @NonNull Replaceable<HelperInstanceResolver> helperInstanceResolver() {
    return replaceable("helperInstanceResolver");
  }

  public @NonNull GlobalManager globalManager() {
    var contextFactory = contextFactory();
    var compensationRegistry = compensationRegistry();
    return new GlobalManager(
            new ProcessManager(new DefaultProcessRegistry(),
                    processRunner(contextFactory, compensationRegistry)),
            new TransactionManager(new DefaultTransactionRegistry(),
                    transactionRunner(contextFactory, compensationRegistry)),
            new HelperManager(new DefaultHelperRegistry(),
                    helperRunner(contextFactory)),
            new GeneratedClassRegistry(),
            new ProcessContextFactory(),
            compensationRegistry);
  }

  public @NonNull CompensationRegistry compensationRegistry() {
    return singleton(DefaultCompensationRegistry::new);
  }

  /* ============= */

  @Getter
  private static final class Holder implements SingletonSupport {

    public static final Holder INSTANCE = new Holder();

    private final SingletonScope scope = SingletonScope.of();

    public DslConfig dslConfig() {
      return dslConfig(scope);
    }

    public DslConfig dslConfig(Scope scope) {
      return singleton(scope.id(), () -> new DslConfig(scope));
    }
  }
}
