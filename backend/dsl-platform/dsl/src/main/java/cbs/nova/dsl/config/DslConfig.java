package cbs.nova.dsl.config;

import cbs.nova.dsl.BeanResolver;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.helper.HelperManager;
import cbs.nova.dsl.jsonschema.JsonSchemaGenerator;
import cbs.nova.dsl.converter.AvajeMapConverter;
import cbs.nova.dsl.explain.ClasspathExplainResourceResolver;
import cbs.nova.dsl.explain.ExplainResourceRegistry;
import cbs.nova.dsl.explain.ExplainResourceResolver;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.security.ObjectGuard;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.model.RetryPolicy;
import cbs.nova.dsl.process.ProcessManager;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.registry.DefaultModelRegistry;
import cbs.nova.dsl.registry.DefaultProcessRegistry;
import cbs.nova.dsl.registry.DefaultTransactionRegistry;
import cbs.nova.dsl.registry.GeneratedClassRegistry;
import cbs.nova.dsl.registry.ModelRegistry;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.runner.HelperRunner;
import cbs.nova.dsl.runner.ProcessCompensationHandler;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.dsl.transaction.TransactionManager;
import cbs.nova.dsl.transaction.TransactionRunner;
import cbs.nova.dsl.utils.ExpressionEvaluator;
import io.avaje.jsonb.Jsonb;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;

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

  public @NonNull ModelRegistry modelRegistry() {
    return singleton(DefaultModelRegistry::discover);
  }

  public @NonNull AvajeMapConverter avajeMapConverter() {
    return singleton(() -> new AvajeMapConverter(Jsonb.builder().build(), modelRegistry()));
  }

  public @NonNull RetryPolicyFactory retryPolicyFactory() {
    return singleton(RetryPolicyFactory::new);
  }

  public @NonNull Replaceable<TransactionInvoker> transactionInvoker() {
    return replaceable("transactionInvoker");
  }

  public @NonNull Replaceable<ExpressionEvaluator> expressionEvaluator() {
    return replaceable("expressionEvaluator");
  }

  public @NonNull Replaceable<ExplainResourceResolver> explainResourceResolver() {
    return replaceable("explainResourceResolver",
            () -> new ClasspathExplainResourceResolver(
                    ClasspathExplainResourceResolver.DEFAULT_PREFIX));
  }

  public @NonNull ExplainResourceRegistry explainResourceRegistry() {
    return singleton(() -> new ExplainResourceRegistry().init(
            Thread.currentThread().getContextClassLoader()));
  }

  public @NonNull Replaceable<BeanResolver> beanResolver() {
    return replaceable("beanResolver", () -> new DslConfigBeanResolver(this));
  }

  public @NonNull Replaceable<TemporalProcessLauncher> temporalProcessLauncher() {
    return replaceable("temporalProcessLauncher");
  }

  public @NonNull ProcessRunner processRunner(
          @NonNull CompensationRegistry compensationRegistry) {
    return singleton(() -> {
      var transactionExecutionRepository = transactionExecutionRepository().get();
      var temporalProcessLauncher = temporalProcessLauncher().get();
      var compensationHandler = new ProcessCompensationHandler(compensationRegistry);
      return new DefaultProcessRunner(transactionExecutionRepository,
              temporalProcessLauncher, compensationHandler);
    });
  }

  public @NonNull TransactionRunner transactionRunner(
          @NonNull CompensationRegistry compensationRegistry) {
    return singleton(() -> new DefaultTransactionRunner(compensationRegistry));
  }

  public @NonNull HelperRunner helperRunner() {
    return singleton(DefaultHelperRunner::new);
  }

  public @NonNull Replaceable<JsonSchemaGenerator> jsonSchemaGenerator() {
    return replaceable("jsonSchemaGenerator");
  }


  public @NonNull Replaceable<ObjectGuard> objectGuard() {
    return replaceable("objectGuard", () -> ObjectGuard.NO_OP);
  }

  public @NonNull Replaceable<HelperInstanceResolver> helperInstanceResolver() {
    return replaceable("helperInstanceResolver");
  }

  public @NonNull Replaceable<TransactionExecutionRepository> transactionExecutionRepository() {
    return singleton("transactionExecutionRepository",
            () -> Replaceable.of(InMemoryTransactionExecutionRepository::new));
  }

  public @NonNull ObjectMapper jsonMapper() {
    return singleton(ObjectMapper::new);
  }

  public @NonNull GeneratedClassRegistry generatedClassRegistry() {
    return singleton(GeneratedClassRegistry::new);
  }

  public @NonNull GlobalManager globalManager() {
    var compensationRegistry = compensationRegistry();
    return new GlobalManager(
            new ProcessManager(new DefaultProcessRegistry(),
                    processRunner(compensationRegistry)),
            new TransactionManager(new DefaultTransactionRegistry(),
                    transactionRunner(compensationRegistry)),
            new HelperManager(new DefaultHelperRegistry(),
                    helperRunner()),
            generatedClassRegistry(),
            compensationRegistry,
            explainResourceRegistry());
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
