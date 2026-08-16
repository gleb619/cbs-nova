package cbs.nova.dsl.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.SingletonSupport.Replaceable;
import cbs.nova.dsl.config.SingletonSupport.SingletonScope;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.model.RetryPolicy;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.runner.HelperRunner;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.dsl.transaction.TransactionRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class DslConfigTest {

  private final DslConfig dsl = new DslConfig(SingletonScope.of());

  @AfterEach
  void resetGlobalState() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void newInstanceExposesConstructorScope() {
    SingletonScope scope = SingletonScope.of();

    DslConfig config = new DslConfig(scope);

    assertThat(config.getScope()).isSameAs(scope);
  }

  @Test
  void distinctInstancesHaveDistinctScopes() {
    DslConfig other = new DslConfig(SingletonScope.of());

    assertThat(dsl.getScope()).isNotSameAs(other.getScope());
  }

  @Test
  void temporalProcessLauncherIsNullByDefault() {
    assertThat(dsl.temporalProcessLauncher().get()).isNull();
  }

  @Test
  void transactionInvokerIsNullByDefault() {
    assertThat(dsl.transactionInvoker().get()).isNull();
  }

  @Test
  void helperInstanceResolverIsNullByDefault() {
    assertThat(dsl.helperInstanceResolver().get()).isNull();
  }

  @Test
  void temporalProcessLauncherRoundTripsThroughReplace() {
    TemporalProcessLauncher stub = new StubLauncher(_ctx -> Result.success("launched"),
            _ctx -> true);

    dsl.temporalProcessLauncher().replace(stub);

    assertThat(dsl.temporalProcessLauncher().get()).isSameAs(stub);
  }

  @Test
  void transactionInvokerRoundTripsThroughReplace() {
    TransactionInvoker invoker = (_name, _input, _ctx) -> Result.success("invoked");

    dsl.transactionInvoker().replace(invoker);

    assertThat(dsl.transactionInvoker().get()).isSameAs(invoker);
  }

  @Test
  void helperInstanceResolverRoundTripsThroughReplace() {
    HelperInstanceResolver resolver = _helperClass -> new Executable<Object, Object>() {
      @Override
      public Result<Object> execute(Context<Object> ctx) {
        return Result.success(new Object());
      }
    };

    dsl.helperInstanceResolver().replace(resolver);

    assertThat(dsl.helperInstanceResolver().get()).isSameAs(resolver);
  }

  @Test
  void contextFactoryReturnsSameInstanceAcrossCalls() {
    ContextFactory first = dsl.contextFactory();
    ContextFactory second = dsl.contextFactory();

    assertThat(first).isNotNull().isSameAs(second);
  }

  @Test
  void defaultRetryPolicyReturnsSameInstanceAcrossCalls() {
    RetryPolicy first = dsl.defaultRetryPolicy();
    RetryPolicy second = dsl.defaultRetryPolicy();

    assertThat(first).isNotNull().isSameAs(second);
    assertThat(first.maxAttempts()).isEqualTo(3);
  }

  @Test
  void compensationRegistryReturnsSameInstanceAcrossCalls() {
    var first = dsl.compensationRegistry();
    var second = dsl.compensationRegistry();

    assertThat(first).isNotNull().isSameAs(second);
  }

  @Test
  void retryPolicyFactoryReturnsSameInstanceAcrossCalls() {
    RetryPolicyFactory first = dsl.retryPolicyFactory();
    RetryPolicyFactory second = dsl.retryPolicyFactory();

    assertThat(first).isNotNull().isSameAs(second);
  }

  @Test
  void processRunnerReturnsSameInstanceForSameArguments() {
    ContextFactory ctxFactory = dsl.contextFactory();
    CompensationRegistry registry = dsl.compensationRegistry();

    ProcessRunner first = dsl.processRunner(ctxFactory, registry);
    ProcessRunner second = dsl.processRunner(ctxFactory, registry);

    assertThat(first).isNotNull().isSameAs(second);
  }

  @Test
  void transactionRunnerReturnsSameInstanceForSameArguments() {
    ContextFactory ctxFactory = dsl.contextFactory();
    CompensationRegistry registry = dsl.compensationRegistry();

    TransactionRunner first = dsl.transactionRunner(ctxFactory, registry);
    TransactionRunner second = dsl.transactionRunner(ctxFactory, registry);

    assertThat(first).isNotNull().isSameAs(second);
  }

  @Test
  void helperRunnerReturnsSameInstanceForSameArguments() {
    ContextFactory ctxFactory = dsl.contextFactory();

    HelperRunner first = dsl.helperRunner(ctxFactory);
    HelperRunner second = dsl.helperRunner(ctxFactory);

    assertThat(first).isNotNull().isSameAs(second);
  }

  @Test
  void processRunnerIsInstanceOfDefaultProcessRunner() {
    ProcessRunner runner = dsl.processRunner(dsl.contextFactory(), dsl.compensationRegistry());

    assertThat(runner).isInstanceOf(DefaultProcessRunner.class);
  }

  @Test
  void transactionRunnerIsInstanceOfDefaultTransactionRunner() {
    TransactionRunner runner = dsl.transactionRunner(dsl.contextFactory(),
            dsl.compensationRegistry());

    assertThat(runner).isInstanceOf(DefaultTransactionRunner.class);
  }

  @Test
  void helperRunnerIsInstanceOfDefaultHelperRunner() {
    HelperRunner runner = dsl.helperRunner(dsl.contextFactory());

    assertThat(runner).isInstanceOf(DefaultHelperRunner.class);
  }

  @Test
  void replaceIsInPlaceMutationNotNewInstance() {
    Replaceable<TemporalProcessLauncher> first = dsl.temporalProcessLauncher();
    Replaceable<TemporalProcessLauncher> second = dsl.temporalProcessLauncher();

    assertThat(first).isSameAs(second);

    TemporalProcessLauncher stub = new StubLauncher(_ctx -> Result.success("x"),
            _ctx -> true);
    first.replace(stub);

    assertThat(second).isSameAs(first);
    assertThat(second.get()).isSameAs(stub);
  }

  @Test
  void replaceWithNullClearsTheOverrideAndFallsBackToFactory() {
    dsl.temporalProcessLauncher().replace(new StubLauncher(_ctx -> Result.success("x"),
            _ctx -> true));
    assertThat(dsl.temporalProcessLauncher().get()).isNotNull();

    dsl.temporalProcessLauncher().replace(null);

    assertThat(dsl.temporalProcessLauncher().get()).isNull();
  }

  @Test
  void defaultProcessRunnerObservesLatestReplacedLauncherViaDslConfig() {
    AtomicInteger launchCalls = new AtomicInteger();
    TemporalProcessLauncher stub = new StubLauncher(
            _ctx -> Result.success("from-stub"),
            _ctx -> {
              launchCalls.incrementAndGet();
              return true;
            });

    DslConfig.dslConfig().temporalProcessLauncher().replace(stub);

    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(_ctx -> Result.success("from-execute"))
            .build();
    Context<String> ctx = dsl.contextFactory().of("input", ExecutionMode.RUN, "run-regression");

    DefaultProcessRunner runner = new DefaultProcessRunner(
            dsl.contextFactory(),
            dsl.compensationRegistry());

    Result<?> result = runner.run(process, ctx);

    assertThat(launchCalls.get())
            .as("DefaultProcessRunner must observe the launcher replaced on DslConfig")
            .isEqualTo(1);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("from-stub");
  }

  @Test
  void defaultProcessRunnerDoesNotCallLauncherAfterReplaceClearsIt() {
    AtomicInteger launchCalls = new AtomicInteger();
    TemporalProcessLauncher stub = new StubLauncher(
            _ctx -> Result.success("from-stub"),
            _ctx -> {
              launchCalls.incrementAndGet();
              return true;
            });

    DslConfig.dslConfig().temporalProcessLauncher().replace(stub);
    DslConfig.dslConfig().temporalProcessLauncher().replace(null);

    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(_ctx -> Result.success("from-execute"))
            .build();
    Context<String> ctx = dsl.contextFactory().of("input", ExecutionMode.RUN, "run-no-launcher");

    DefaultProcessRunner runner = new DefaultProcessRunner(
            dsl.contextFactory(),
            dsl.compensationRegistry());

    Result<?> result = runner.run(process, ctx);

    assertThat(launchCalls.get())
            .as("Cleared launcher must not be invoked")
            .isZero();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("from-execute");
  }

  @Test
  void processRunnerDoesNotRejectNullArgsAtRuntime() {
    ProcessRunner runner = dsl.processRunner(null, null);

    assertThat(runner).isNotNull();
  }

  @Test
  void transactionRunnerDoesNotRejectNullArgsAtRuntime() {
    TransactionRunner runner = dsl.transactionRunner(null, null);

    assertThat(runner).isNotNull();
  }

  @Test
  void helperRunnerDoesNotRejectNullArgsAtRuntime() {
    HelperRunner runner = dsl.helperRunner(null);

    assertThat(runner).isNotNull();
  }

  private record StubLauncher(
          LauncherFn launchFn,
          CanRunFn canRunFn) implements TemporalProcessLauncher {

    @Override
    public boolean canRun(Context<?> ctx) {
      return canRunFn.test(ctx);
    }

    @Override
    public Result<?> launch(
            String processName,
            String taskQueue,
            Class<?> inputType,
            Class<?> outputType,
            Context<?> ctx) {
      return launchFn.launch(ctx);
    }
  }

  @FunctionalInterface
  private interface LauncherFn {
    Result<?> launch(Context<?> ctx);
  }

  @FunctionalInterface
  private interface CanRunFn {
    boolean test(Context<?> ctx);
  }
}
