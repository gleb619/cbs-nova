package cbs.nova.dsl;

import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.security.ObjectGuard;
import cbs.nova.dsl.exception.DslEntityNotFoundException;
import cbs.nova.dsl.exception.DslExecutionException;
import cbs.nova.dsl.explain.ExplainResource;
import cbs.nova.dsl.explain.ExplainResourceProvider;
import cbs.nova.dsl.explain.ExplainResourceRegistry;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.helper.HelperManager;
import cbs.nova.dsl.helper.HelperResolver;
import cbs.nova.dsl.process.ProcessCompensation;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.process.ProcessMain;
import cbs.nova.dsl.process.ProcessManager;
import cbs.nova.dsl.registry.GeneratedClassRegistry;
import cbs.nova.dsl.listener.DefaultExecutionListener;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.CompensationRichContext;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.dsl.transaction.TransactionManager;
import cbs.nova.dsl.transaction.TransactionRouting;
import cbs.nova.dsl.utils.Strings;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@RequiredArgsConstructor
public final class GlobalManager {

  private static final AtomicReference<GlobalManager> INSTANCE = new AtomicReference<>();

  private final ProcessManager processManager;
  private final TransactionManager transactionManager;
  private final HelperManager helperManager;
  private final GeneratedClassRegistry generatedClassRegistry;
  private final CompensationRegistry compensationRegistry;
  private final ExplainResourceRegistry explainResourceRegistry;

  public static @NonNull GlobalManager globalManager() {
    var instance = INSTANCE.get();
    if (instance == null) {
      var created = DslConfig.dslConfig().globalManager();
      if (INSTANCE.compareAndSet(null, created)) {
        return created;
      }
      return INSTANCE.get();
    }
    return instance;
  }

  public void registerProcess(@NonNull ProcessDslObject process) {
    processManager.register(process);
  }

  public void registerTransaction(@NonNull TransactionDslObject tx) {
    transactionManager.register(tx);
  }

  public void registerHelper(@NonNull String name, @NonNull Executable<?, ?> helper) {
    helperManager.register(name, helper);
  }

  public void registerHelper(@NonNull String name,
          @NonNull Supplier<Executable<?, ?>> helperSupplier) {
    helperManager.register(name, helperSupplier);
  }

  public void registerHelpers(@NonNull HelperResolver resolver) {
    resolver.registerHelpers(helperManager, DslConfig.dslConfig().helperInstanceResolver().get());
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
    return SimpleContext.builder().body(body).metadata(metadata).mode(mode).runId(runId).build();
  }

  @Deprecated(forRemoval = true)
  public @NonNull Context<?> createContext(
          @NonNull Object body,
          @NonNull Map<String, Object> metadata,
          @NonNull ExecutionMode mode,
          @NonNull String runId,
          @NonNull TransactionRouting transactionRouting) {
    return SimpleContext.builder().body(body).metadata(metadata).mode(mode).runId(runId)
            .transactionRouting(transactionRouting).build();
  }

  public @NonNull CompensationRichContext<?> createCompensationContext(
          @NonNull Context<?> ctx,
          @NonNull Throwable error) {
    return new CompensationRichContext<>(ctx, error);
  }

  public @NonNull Result<?> runProcess(
          @NonNull ProcessDslObject process,
          @NonNull Context<?> ctx) {
    return processManager.execute(process, ctx);
  }

  public @NonNull Result<?> runProcess(@NonNull String name, @NonNull Context<?> ctx) {
    return processManager.find(name)
            .map(p -> runProcess(p, ctx))
            .orElse(Result.failure(
                    new DslEntityNotFoundException(ctx.runId(), "Process not found: " + name)));
  }

  public @NonNull Result<?> runProcess(
          @NonNull String name,
          @NonNull String version,
          @NonNull Context<?> ctx) {
    return processManager.find(name, version)
            .map(p -> runProcess(p, ctx))
            .orElse(Result.failure(
                    new DslEntityNotFoundException(
                            ctx.runId(),
                            "Process not found: " + name + " version " + version)));
  }

  public @NonNull Result<?> runTransaction(
          @NonNull TransactionDslObject tx,
          @NonNull Context<?> ctx) {
    return transactionManager.execute(tx, ctx);
  }

  public @NonNull Result<?> runTransaction(
          @NonNull TransactionDslObject tx,
          @NonNull Object input,
          @NonNull Context<?> parentCtx) {
    Context<Object> ctx = SimpleContext.builder()
            .body(input)
            .metadata(parentCtx.metadata())
            .mode(parentCtx.mode())
            .runId(parentCtx.runId())
            .transactionRouting(parentCtx.transactionRouting())
            .executionListener(parentCtx.executionListener())
            .saga(parentCtx.saga())
            .helperInterceptor(parentCtx.helperInterceptor())
            .build();
    return runTransaction(tx, ctx);
  }

  public @NonNull Result<?> runTransaction(@NonNull String name, @NonNull Context<?> ctx) {
    return transactionManager.find(name)
            .map(t -> runTransaction(t, ctx))
            .orElse(Result.failure(
                    new DslEntityNotFoundException(ctx.runId(), "Transaction not found: " + name)));
  }

  public @NonNull Result<?> runTransaction(
          @NonNull String name, @NonNull Object input, @NonNull Context<?> parentCtx) {
    return transactionManager.find(name)
            .map(t -> runTransaction(t, input, parentCtx))
            .orElse(Result.failure(
                    new DslEntityNotFoundException(
                            parentCtx.runId(), "Transaction not found: " + name)));
  }

  public @NonNull Object runTransactionWithCompensation(
          @NonNull TransactionDslObject tx,
          @NonNull String runId,
          @NonNull Object input) {
    var saga = DslSaga.create();
    var ctx = createContext(input, Map.of(), ExecutionMode.RUN, runId)
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY)
            .withSaga(saga);
    var result = runTransaction(tx, ctx);
    if (!result.isSuccess()) {
      saga.compensate();
      throw new RuntimeException("Transaction failed", result.cause());
    }
    return result.value();
  }

  public @NonNull Object runTransactionWithCompensation(
          @NonNull String name,
          @NonNull String runId,
          @NonNull Object input) {
    return transactionManager.find(name)
            .map(tx -> runTransactionWithCompensation(tx, runId, input))
            .orElseThrow(() -> new RuntimeException(
                    "Transaction failed",
                    new DslEntityNotFoundException(runId, "Transaction not found: " + name)));
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

  public @NonNull Optional<FunctionDslObject> findFunction(@NonNull String name) {
    return helperManager.findFunction(name);
  }

  public @NonNull Optional<DslDescriptor> describeProcess(@NonNull String name) {
    return findProcess(name).map(ProcessDslObject::descriptor);
  }

  public @NonNull Optional<DslDescriptor> describeTransaction(@NonNull String name) {
    return findTransaction(name).map(TransactionDslObject::describe);
  }

  public @NonNull Optional<DslDescriptor> describeFunction(@NonNull String name) {
    return helperManager.findFunction(name).map(FunctionDslObject::descriptor);
  }

  public @NonNull Optional<GeneratedClassDescriptor> findGeneratedProcess(@NonNull String name) {
    return generatedClassRegistry.findProcess(name);
  }

  public @NonNull Optional<GeneratedClassDescriptor> findGeneratedTransaction(
          @NonNull String name) {
    return generatedClassRegistry.findTransaction(name);
  }

  public @NonNull Optional<String> findFilename(@NonNull String name) {
    return generatedClassRegistry.findFilename(name);
  }

  public boolean hasGeneratedProcess(@NonNull String name) {
    return generatedClassRegistry.findProcess(name).isPresent();
  }

  @Deprecated(forRemoval = true)
  public boolean hasGeneratedTransaction(@NonNull String name) {
    return generatedClassRegistry.findTransaction(name).isPresent();
  }

  @Deprecated(forRemoval = true)
  public @NonNull List<GeneratedClassDescriptor> generatedProcesses() {
    return generatedClassRegistry.processes();
  }

  @Deprecated(forRemoval = true)
  public @NonNull List<GeneratedClassDescriptor> generatedTransactions() {
    return generatedClassRegistry.transactions();
  }

  public void registerGeneratedClass(@NonNull GeneratedClassDescriptor descriptor) {
    generatedClassRegistry.register(descriptor);
  }

  public void registerGeneratedClass(@NonNull GeneratedClassProvider provider) {
    generatedClassRegistry.register(provider);
  }

  public @NonNull Optional<TransactionInvoker> transactionInvoker() {
    return Optional.ofNullable(DslConfig.dslConfig().transactionInvoker().get());
  }

  public boolean registerTransactionCompensation(
          @NonNull String name,
          @NonNull String runId,
          @NonNull Context<?> baseCtx) {
    return transactionManager.find(name)
            .map(tx -> compensationRegistry.register(name, runId, baseCtx, tx))
            .orElse(false);
  }

  public void compensateTransaction(@NonNull String name, @NonNull String runId,
          @NonNull Throwable error) {
    var config = DslConfig.dslConfig();
    compensationRegistry.compensate(name, runId, error);
  }

  public void compensateTransaction(
          @NonNull TransactionDslObject tx,
          @NonNull Context<?> ctx,
          @NonNull Throwable error) {
    if (tx.compensationLogic() == null) {
      return;
    }
    tx.compensationLogic().apply(createCompensationContext(ctx, error));
  }

  public void compensateTransaction(
          @NonNull String name,
          @NonNull Context<?> ctx,
          @NonNull Throwable error) {
    transactionManager.find(name).ifPresent(tx -> compensateTransaction(tx, ctx, error));
  }

  public void compensateTransaction(
          @NonNull TransactionDslObject tx,
          @NonNull String runId,
          @NonNull Object input,
          @NonNull Throwable error) {
    var ctx = createContext(input, Map.of(), ExecutionMode.COMPENSATION, runId);
    compensateTransaction(tx, ctx, error);
  }

  public void compensateTransaction(
          @NonNull String name,
          @NonNull String runId,
          @NonNull Object input,
          @NonNull Throwable error) {
    transactionManager.find(name).ifPresent(tx -> compensateTransaction(tx, runId, input, error));
  }

  public void compensateProcess(
          @NonNull ProcessDslObject process,
          @NonNull Context<?> ctx,
          @NonNull Throwable error) {
    if (process.compensationLogic() == null) {
      return;
    }
    process.compensationLogic().accept(createCompensationContext(ctx, error), List.of());
  }

  public void compensateProcess(
          @NonNull String name,
          @NonNull Context<?> ctx,
          @NonNull Throwable error) {
    findProcess(name).ifPresent(p -> compensateProcess(p, ctx, error));
  }

  public @NonNull Object runProcessWithCompensation(
          @NonNull String runId,
          @NonNull Object input,
          @NonNull ProcessDslObject process) {
    Map<String, Object> metadata = new HashMap<>();
    if (DslConfig.dslConfig().objectGuard().get().active()) {
      metadata.put(Constants.DSL_DEFINITION_NAME_METADATA_KEY, process.name());
    }
    return runProcessWithCompensation(
            runId,
            input,
            ctx -> runProcess(process, ctx),
            (compCtx, error) -> compensateProcess(process, compCtx, error),
            metadata);
  }

  public @NonNull Object runProcessWithCompensation(
          @NonNull String runId,
          @NonNull Object input,
          @NonNull ProcessDslObject process,
          @NonNull Map<String, Object> extraMetadata) {
    Map<String, Object> metadata = new HashMap<>(extraMetadata);
    if (DslConfig.dslConfig().objectGuard().get().active()) {
      metadata.put(Constants.DSL_DEFINITION_NAME_METADATA_KEY, process.name());
    }
    return runProcessWithCompensation(
            runId,
            input,
            ctx -> runProcess(process, ctx),
            (compCtx, error) -> compensateProcess(process, compCtx, error),
            metadata);
  }

  public @NonNull Object runProcessWithCompensation(
          @NonNull String runId,
          @NonNull Object input,
          @NonNull ProcessMain main,
          @NonNull ProcessCompensation compensation) {
    return runProcessWithCompensation(runId, input, main, compensation, Map.of());
  }

  private @NonNull Object runProcessWithCompensation(
          @NonNull String runId,
          @NonNull Object input,
          @NonNull ProcessMain main,
          @NonNull ProcessCompensation compensation,
          @NonNull Map<String, Object> metadata) {
    var saga = DslSaga.create();
    var repository = DslConfig.dslConfig().transactionExecutionRepository().get();
    var listener = new DefaultExecutionListener(runId, repository);
    var ctx = createContext(input, metadata, ExecutionMode.RUN, runId)
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY)
            .withExecutionListener(listener)
            .withSaga(saga);
    var compCtx = createContext(input, Map.of(), ExecutionMode.COMPENSATION, runId);
    var failureRef = new AtomicReference<Throwable>();

    saga.addCompensation(() -> compensation.accept(compCtx,
            failureRef.get() != null
                    ? failureRef.get()
                    : new RuntimeException("compensation triggered")));

    try {
      var result = main.apply(ctx);
      if (!result.isSuccess()) {
        failureRef.set(result.cause());
        saga.compensate();
        String detail = result.cause() != null ? result.cause().getMessage() : "unknown";
        throw new DslExecutionException(ctx.runId(), "Process failed: " + detail,
                result.cause() != null ? result.cause() : new RuntimeException("unknown"));
      }
      return result.value();
    } catch (Exception e) {
      failureRef.set(e);
      saga.compensate();
      throw e;
    }
  }

  public @NonNull Optional<String> description(@NonNull String name) {
    return describeProcess(name).map(DslDescriptor::description)
            .or(() -> describeTransaction(name).map(DslDescriptor::description))
            .or(() -> describeHelper(name).map(ExecutableDescriptor::description))
            .or(() -> findHelper(name).map(Executable::description))
            .or(() -> describeFunction(name).map(DslDescriptor::description));
  }

  public void resetForTests() {
    INSTANCE.set(null);
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);
    DslConfig.dslConfig().transactionInvoker().replace(null);
    DslConfig.dslConfig().objectGuard().replace(ObjectGuard.NO_OP);
  }

  public void replaceGlobalManager(@NonNull GlobalManager replacement) {
    INSTANCE.set(replacement);
  }

  public ClassLoader defaultClassLoader() {
    return GlobalManager.class.getClassLoader();
  }

  public void registerExplainResource(@NonNull ExplainResourceProvider provider) {
    explainResourceRegistry.register(provider);
  }

  public void registerExplainResources(@NonNull ClassLoader classLoader) {
    explainResourceRegistry.init(classLoader);
  }

  public @NonNull Optional<ExplainResource> describeExplainResource(@NonNull String name) {
    return explainResourceRegistry.describeByName(name);
  }

  public @NonNull Optional<ExplainResource> describeExplainResourceByFilename(
          @NonNull String filename) {
    return explainResourceRegistry.describeByFilename(filename);
  }

  /**
   * Resolves the markdown content for an explain resource associated with the given DSL object
   * {@code name}. Lookup order: frontmatter {@code name}, then {@code name + ".md"}, then the
   * kebab-cased variant (e.g. {@code BatchProcessing -> batch-processing.md}). Falls back to
   * {@link Constants#EMPTY_MARKDOWN} when nothing matches.
   */
  public @NonNull String resolveExplainContent(@NonNull String name) {
    return explainResourceRegistry.describeByName(name)
            .or(() -> explainResourceRegistry.describeByFilename(name + ".md"))
            .or(() -> explainResourceRegistry.describeByFilename(Strings.toKebabCase(name) + ".md"))
            .map(ExplainResource::content)
            .orElse(Constants.EMPTY_MARKDOWN);
  }

}
