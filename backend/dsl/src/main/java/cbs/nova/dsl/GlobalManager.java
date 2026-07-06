package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

public final class GlobalManager {

  private static volatile GlobalManager INSTANCE;

  private final ProcessManager processManager;
  private final TransactionManager transactionManager;
  private final HelperManager helperManager;

  private GlobalManager() {
    this.processManager = new ProcessManager(new DefaultProcessRegistry(),
            new DefaultProcessRunner());
    this.transactionManager = new TransactionManager(new DefaultTransactionRegistry(),
            new DefaultTransactionRunner());
    this.helperManager = new HelperManager(new DefaultHelperRegistry(), new DefaultHelperRunner());
  }

  public static @NonNull GlobalManager getInstance() {
    if (INSTANCE == null) {
      synchronized (GlobalManager.class) {
        if (INSTANCE == null)
          INSTANCE = new GlobalManager();
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

  public void registerFunction(@NonNull FunctionDslObject fn) {
    helperManager.registerFunction(fn);
  }

  public @NonNull Result<?> runProcess(@NonNull String name, @NonNull Context<?> ctx) {
    return processManager.execute(name, ctx);
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
            .map(h -> ((Executable<Object, Object>) h).describe());
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

  public static void resetForTests() {
    INSTANCE = null;
  }
}
