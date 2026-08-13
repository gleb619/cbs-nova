package cbs.nova.dsl.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CompensationRegistry;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.HelperInstanceResolver;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.RetryPolicy;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.config.SingletonSupport.Replaceable;
import cbs.nova.dsl.config.SingletonSupport.SingletonScope;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.runner.HelperRunner;
import cbs.nova.dsl.transaction.TransactionRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for {@link DslConfig}.
 *
 * <p>
 * Most tests use an isolated {@link DslConfig} instance bound to a fresh {@link SingletonScope} so
 * the global {@code DslConfig.dslConfig()} slot is not touched. The launcher regression guard (the
 * {@code ed55763} contract) is the one test that deliberately exercises the static
 * {@code DslConfig.dslConfig()} path; it clears the slot in {@code @AfterEach} via
 * {@link GlobalManager#resetForTests()}.
 */
class DslConfigTest {

  private final DslConfig dsl = new DslConfig(SingletonScope.of());

  @AfterEach
  void resetGlobalState() {
    // Clear any static slot mutated by the launcher-regression guard test so
    // ordering with other tests in the suite is order-independent.
    GlobalManager.globalManager().resetForTests();
  }

  // --- Construction & scope accessor --------------------------------------

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

  // --- Default/empty behavior --------------------------------------------

  @Test
  void temporalProcessLauncherIsNullByDefault() {
    // The factory backing the Replaceable is `() -> null`; no override is set.
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

  // --- Held-dependency round-trips: Replaceable<T> mutators ----------------

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

  // --- Held-dependency round-trips: cached singletons ---------------------

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
    CompensationRegistry first = dsl.compensationRegistry();
    CompensationRegistry second = dsl.compensationRegistry();

    assertThat(first).isNotNull().isSameAs(second);
  }

  @Test
  void retryPolicyFactoryReturnsSameInstanceAcrossCalls() {
    RetryPolicyFactory first = dsl.retryPolicyFactory();
    RetryPolicyFactory second = dsl.retryPolicyFactory();

    assertThat(first).isNotNull().isSameAs(second);
  }

  // --- ProcessRunner / TransactionRunner / HelperRunner factories ----------

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

  // --- replace(...) semantics: in-place mutation, same instance -----------
  // ed55763 contract: `Replaceable.replace(value)` mutates the existing
  // holder in place; subsequent `.get()` returns the new value. The same
  // `Replaceable` reference is reused across calls (it is a singleton in
  // the scope).

  @Test
  void replaceIsInPlaceMutationNotNewInstance() {
    Replaceable<TemporalProcessLauncher> first = dsl.temporalProcessLauncher();
    Replaceable<TemporalProcessLauncher> second = dsl.temporalProcessLauncher();

    assertThat(first).isSameAs(second);

    TemporalProcessLauncher stub = new StubLauncher(_ctx -> Result.success("x"),
            _ctx -> true);
    first.replace(stub);

    // Same instance, mutated — second call sees the replacement.
    assertThat(second).isSameAs(first);
    assertThat(second.get()).isSameAs(stub);
  }

  @Test
  void replaceWithNullClearsTheOverrideAndFallsBackToFactory() {
    dsl.temporalProcessLauncher().replace(new StubLauncher(_ctx -> Result.success("x"),
            _ctx -> true));
    assertThat(dsl.temporalProcessLauncher().get()).isNotNull();

    dsl.temporalProcessLauncher().replace(null);

    // Factory is `() -> null` → get() returns null again.
    assertThat(dsl.temporalProcessLauncher().get()).isNull();
  }

  // --- Regression guard: DefaultProcessRunner reads latest replaced launcher
  // The pre-ed55763 bug was that the launcher class held its own snapshot of
  // the launcher instead of going through DslConfig each call, so a
  // post-construction replace(...) was silently ignored. This test pins
  // the fix: replacing on the static DslConfig.dslConfig() is observed by
  // the next DefaultProcessRunner.run(...) call.

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

  // --- @NonNull contract pin ---------------------------------------------
  // The DslConfig API uses `org.jspecify.annotations.NonNull` on its
  // method parameters (processRunner / transactionRunner / helperRunner).
  // No runtime null-checker (no JSpecify runtime artifact, no NullAway,
  // no Checker Framework) is configured for this module, so passing null
  // for a `@NonNull` parameter is observed to NOT throw at runtime — the
  // null propagates into the constructed object graph instead. These
  // tests pin the observed behavior so the contract is explicit. If a
  // runtime null-checker is ever added, these tests will fail and
  // document the intended rejection semantics (NPE).

  @Test
  void processRunnerDoesNotRejectNullArgsAtRuntime() {
    // No assertion on a thrown exception: per current source there is none.
    // We assert the call returns a non-null ProcessRunner (the call
    // succeeds with nulls leaking through), which is the observable
    // current behavior.
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

  // --- Stub ---------------------------------------------------------------

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
