package cbs.nova.dsl;

import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.context.DefaultProcessContextFactory;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.ProcessManager;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.registry.DefaultProcessRegistry;
import cbs.nova.dsl.registry.DefaultTransactionRegistry;
import cbs.nova.dsl.registry.GeneratedClassRegistry;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.transaction.TransactionManager;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

@RequiredArgsConstructor
public final class GlobalManager {

  private static volatile GlobalManager INSTANCE;

  private final ProcessManager processManager;
  private final TransactionManager transactionManager;
  private final HelperManager helperManager;
  private final GeneratedClassRegistry generatedClassRegistry;
  private final ProcessContextFactory processContextFactory;
  private final CompensationRegistry compensationRegistry;

  public static @NonNull GlobalManager getInstance() {
    if (INSTANCE == null) {
      synchronized (GlobalManager.class) {
        if (INSTANCE == null) {
          var config = DslConfig.dslConfig();
          var traceCollector = config.executionTraceCollector();
          var contextFactory = config.contextFactory();
          INSTANCE = new GlobalManager(
                  new ProcessManager(new DefaultProcessRegistry(),
                          config.processRunner(traceCollector, contextFactory)),
                  new TransactionManager(new DefaultTransactionRegistry(),
                          config.transactionRunner(traceCollector, contextFactory)),
                  new HelperManager(new DefaultHelperRegistry(),
                          config.helperRunner(traceCollector, contextFactory)),
                  new GeneratedClassRegistry(),
                  new DefaultProcessContextFactory(),
                  new CompensationRegistry());
        }
      }
    }
    return INSTANCE;
  }

  public void registerProcess(@NonNull ProcessDslObject process) {
    processManager.register(process);
  }

  public void registerTransaction(@NonNull TransactionDslObject tx) {
    transactionManager.register(tx);
  }

  public void registerHelper(@NonNull String name, @NonNull Executable<?, ?> helper) {
    helperManager.registerHelper(name, helper);
  }

  public void registerHelpers(@NonNull HelperResolver resolver) {
    resolver.registerHelpers(helperManager::registerHelper);
  }

  public void registerHelperResolvers() {
    var classLoader = Thread.currentThread().getContextClassLoader();
    ServiceLoader.load(HelperResolver.class, classLoader).forEach(this::registerHelpers);
  }

  public void registerFunction(@NonNull FunctionDslObject fn) {
    helperManager.registerFunction(fn);
  }

  public @NonNull Context<?> createContext(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId) {
    return processContextFactory.create(body, metadata, mode, runId);
  }

  public @NonNull Context<?> createContext(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting) {
    return processContextFactory.create(body, metadata, mode, runId, transactionRouting);
  }

  public @NonNull CompensationRichContext<?> createCompensationContext(
          @NonNull Context<?> ctx,
          @NonNull Throwable error) {
    var config = DslConfig.dslConfig();
    return new CompensationRichContext<>(ctx, error, config.executionTraceCollector(),
            config.contextFactory());
  }

  public @NonNull Result<?> runProcess(@NonNull String name, @NonNull Context<?> ctx) {
    return processManager.execute(name, ctx);
  }

  public @NonNull Result<?> runTransaction(
          @NonNull String name, @NonNull Object input, @NonNull Context<?> parentCtx) {
    Context<Object> ctx = DslConfig.dslConfig().contextFactory()
            .of(input, parentCtx.metadata(), parentCtx.mode(), parentCtx.runId(),
                    parentCtx.transactionRouting(), parentCtx.executionListener());
    return transactionManager.execute(name, ctx);
  }

  public @NonNull Result<?> runTransaction(@NonNull String name, @NonNull Context<?> ctx) {
    return transactionManager.execute(name, ctx);
  }

  public @NonNull Result<?> runHelper(@NonNull String name, @NonNull Context<?> ctx) {
    return helperManager.executeHelper(name, ctx);
  }

  public @NonNull Result<?> runFunction(@NonNull String name, @NonNull Context<?> ctx) {
    return helperManager.executeFunction(name, ctx);
  }

  public boolean hasProcess(@NonNull String name) {
    return processManager.contains(name);
  }

  public boolean hasTransaction(@NonNull String name) {
    return transactionManager.contains(name);
  }

  public boolean hasHelper(@NonNull String name) {
    return helperManager.contains(name);
  }

  public @NonNull Optional<ProcessDslObject> findProcess(@NonNull String name) {
    return processManager.find(name);
  }

  public @NonNull Optional<TransactionDslObject> findTransaction(@NonNull String name) {
    return transactionManager.find(name);
  }

  public @NonNull List<String> processNames() {
    return processManager.names();
  }

  public @NonNull List<String> transactionNames() {
    return transactionManager.names();
  }

  public @NonNull List<String> helperNames() {
    return helperManager.names();
  }

  @SuppressWarnings("unchecked")
  public @NonNull Optional<ExecutableDescriptor> describeHelper(@NonNull String name) {
    return helperManager.findHelper(name)
            .map(Executable::describe);
  }

  public @NonNull Optional<Executable<?, ?>> findHelper(@NonNull String name) {
    return helperManager.findHelper(name);
  }

  public @NonNull Optional<DslDescriptor> describeProcess(@NonNull String name) {
    return findProcess(name).map(ProcessDslObject::describe);
  }

  public @NonNull Optional<DslDescriptor> describeTransaction(@NonNull String name) {
    return findTransaction(name).map(TransactionDslObject::describe);
  }

  public @NonNull Optional<DslDescriptor> describeFunction(@NonNull String name) {
    return helperManager.findFunction(name).map(FunctionDslObject::describe);
  }

  public @NonNull Optional<GeneratedClassDescriptor> findGeneratedProcess(@NonNull String name) {
    return generatedClassRegistry.findProcess(name);
  }

  public @NonNull Optional<GeneratedClassDescriptor> findGeneratedTransaction(
          @NonNull String name) {
    return generatedClassRegistry.findTransaction(name);
  }

  public boolean hasGeneratedProcess(@NonNull String name) {
    return generatedClassRegistry.findProcess(name).isPresent();
  }

  public boolean hasGeneratedTransaction(@NonNull String name) {
    return generatedClassRegistry.findTransaction(name).isPresent();
  }

  public @NonNull List<GeneratedClassDescriptor> generatedProcesses() {
    return generatedClassRegistry.processes();
  }

  public @NonNull List<GeneratedClassDescriptor> generatedTransactions() {
    return generatedClassRegistry.transactions();
  }

  public void registerGeneratedClass(@NonNull GeneratedClassDescriptor descriptor) {
    generatedClassRegistry.register(descriptor);
  }

  public @NonNull Optional<TransactionInvoker> transactionInvoker() {
    return Optional.ofNullable(DslConfig.dslConfig().transactionInvoker().get());
  }

  /**
   * Registers a compensation for the given transaction and run id. Returns {@code true} when the
   * transaction exists and has a compensation block.
   */
  public boolean registerTransactionCompensation(
          @NonNull String name,
          @NonNull String runId,
          @NonNull Context<?> baseCtx) {
    return transactionManager.find(name)
            .map(tx -> compensationRegistry.register(name, runId, baseCtx, tx))
            .orElse(false);
  }

  /** Invokes a previously registered transaction compensation, if any. */
  public void compensateTransaction(@NonNull String name, @NonNull String runId,
          @NonNull Throwable error) {
    var config = DslConfig.dslConfig();
    compensationRegistry.compensate(name, runId, error, config.executionTraceCollector(),
            config.contextFactory());
  }

  /** Compensates a transaction directly, handling the find/if-null boilerplate internally. */
  public void compensateTransaction(
          @NonNull String name,
          @NonNull Context<?> ctx,
          @NonNull Throwable error) {
    transactionManager.find(name).ifPresent(tx -> {
      if (tx.compensationLogic() == null) {
        return;
      }
      tx.compensationLogic().apply(createCompensationContext(ctx, error));
    });
  }

  /** Compensates a process directly, handling the find/if-null boilerplate internally. */
  public void compensateProcess(
          @NonNull String name,
          @NonNull Context<?> ctx,
          @NonNull Throwable error) {
    findProcess(name).ifPresent(p -> {
      if (p.compensationLogic() == null) {
        return;
      }
      p.compensationLogic().apply(createCompensationContext(ctx, error));
    });
  }

  public void resetForTests() {
    INSTANCE = null;
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);
    DslConfig.dslConfig().transactionInvoker().replace(null);
  }
}
