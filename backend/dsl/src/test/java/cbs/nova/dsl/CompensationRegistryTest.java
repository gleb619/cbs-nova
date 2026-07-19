package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.transaction.TransactionDslObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

class CompensationRegistryTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();

  private CompensationRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new CompensationRegistry();
  }

  @Test
  void registerReturnsFalseAndStoresNothingWhenCompensationLogicIsNull() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var tx = tx("NoComp", null);

    var result = registry.register("NoComp", "run-1", ctx, tx);

    assertThat(result).isFalse();
    assertThat(registry.hasCompensation("run-1")).isFalse();
  }

  @Test
  void registerReturnsTrueAndStoresEntryWhenCompensationLogicIsPresent() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var tx = tx("WithComp", marker("registered", new ArrayList<>()));

    var result = registry.register("WithComp", "run-1", ctx, tx);

    assertThat(result).isTrue();
    assertThat(registry.hasCompensation("run-1")).isTrue();
  }

  @Test
  void compensateInvokesMatchingEntryOnceWithPassedErrorAndRemovesIt() {
    var order = new ArrayList<String>();
    var captured = new AtomicReference<Throwable>();
    var error = new RuntimeException("boom");
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var tx = tx("Tx", marker("Tx", order, captured));

    registry.register("Tx", "run-1", ctx, tx);
    registry.compensate("Tx", "run-1", error, traceCollector, contextFactory);

    assertThat(order).containsExactly("Tx");
    assertThat(captured.get()).isSameAs(error);
    assertThat(registry.hasCompensation("run-1")).isFalse();
  }

  @Test
  void compensateSearchesLifoWithinRunId() {
    var order = new ArrayList<String>();
    var error = new RuntimeException("boom");
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var first = tx("SameName", marker("first", order));
    var second = tx("SameName", marker("second", order));

    registry.register("SameName", "run-1", ctx, first);
    registry.register("SameName", "run-1", ctx, second);

    registry.compensate("SameName", "run-1", error, traceCollector, contextFactory);
    assertThat(order).containsExactly("second");
    assertThat(registry.hasCompensation("run-1")).isTrue();

    registry.compensate("SameName", "run-1", error, traceCollector, contextFactory);
    assertThat(order).containsExactly("second", "first");
    assertThat(registry.hasCompensation("run-1")).isFalse();
  }

  @Test
  void compensateIsNoOpForUnknownRunId() {
    registry.compensate("Tx", "unknown-run", new RuntimeException("boom"),
            traceCollector, contextFactory);
    assertThat(registry.hasCompensation("unknown-run")).isFalse();
  }

  @Test
  void compensateIsNoOpForUnknownTransactionName() {
    var order = new ArrayList<String>();
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var tx = tx("TxA", marker("TxA", order));

    registry.register("TxA", "run-1", ctx, tx);
    registry.compensate("TxB", "run-1", new RuntimeException("boom"),
            traceCollector, contextFactory);

    assertThat(order).isEmpty();
    assertThat(registry.hasCompensation("run-1")).isTrue();
  }

  @Test
  void compensateAllInvokesEveryEntryInReverseRegistrationOrderAndClearsRunId() {
    var order = new ArrayList<String>();
    var captured = new AtomicReference<Throwable>();
    var error = new RuntimeException("boom");
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");

    registry.register("T1", "run-1", ctx, tx("T1", marker("T1", order, captured)));
    registry.register("T2", "run-1", ctx, tx("T2", marker("T2", order, captured)));
    registry.register("T3", "run-1", ctx, tx("T3", marker("T3", order, captured)));

    registry.compensateAll("run-1", error, traceCollector, contextFactory);

    assertThat(order).containsExactly("T3", "T2", "T1");
    assertThat(captured.get()).isSameAs(error);
    assertThat(registry.hasCompensation("run-1")).isFalse();
  }

  @Test
  void compensateAllIsNoOpForUnknownRunId() {
    registry.compensateAll("unknown-run", new RuntimeException("boom"),
            traceCollector, contextFactory);
    assertThat(registry.hasCompensation("unknown-run")).isFalse();
  }

  @Test
  void multiRunIdIsolation() {
    var order1 = new ArrayList<String>();
    var order2 = new ArrayList<String>();
    var ctx1 = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var ctx2 = contextFactory.of("body", ExecutionMode.RUN, "run-2");

    registry.register("T1", "run-1", ctx1, tx("T1", marker("T1", order1)));
    registry.register("T2", "run-2", ctx2, tx("T2", marker("T2", order2)));

    assertThat(registry.hasCompensation("run-1")).isTrue();
    assertThat(registry.hasCompensation("run-2")).isTrue();

    registry.compensate("T1", "run-1", new RuntimeException("boom"),
            traceCollector, contextFactory);
    assertThat(order1).containsExactly("T1");
    assertThat(order2).isEmpty();
    assertThat(registry.hasCompensation("run-1")).isFalse();
    assertThat(registry.hasCompensation("run-2")).isTrue();

    registry.compensateAll("run-2", new RuntimeException("all-boom"),
            traceCollector, contextFactory);
    assertThat(order2).containsExactly("T2");
    assertThat(registry.hasCompensation("run-2")).isFalse();
  }

  @Test
  void clearWipesAllRunIds() {
    var ctx1 = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var ctx2 = contextFactory.of("body", ExecutionMode.RUN, "run-2");

    registry.register("T1", "run-1", ctx1, tx("T1", marker(null, null)));
    registry.register("T2", "run-2", ctx2, tx("T2", marker(null, null)));

    registry.clear();

    assertThat(registry.hasCompensation("run-1")).isFalse();
    assertThat(registry.hasCompensation("run-2")).isFalse();
  }

  private TransactionDslObject tx(String name,
          Function<CompensationContext<?>, Result<?>> compensationLogic) {
    return new TransactionDslObject(
            name,
            "test-queue",
            "v1",
            null,
            null,
            null,
            c -> Result.success(null),
            compensationLogic,
            Duration.ofSeconds(10),
            null,
            null,
            null,
            null);
  }

  private Function<CompensationContext<?>, Result<?>> marker(String marker, List<String> order) {
    return marker(marker, order, null);
  }

  private Function<CompensationContext<?>, Result<?>> marker(
          String marker,
          List<String> order,
          AtomicReference<Throwable> errorRef) {
    return ctx -> {
      if (errorRef != null) {
        errorRef.set(ctx.error());
      }
      if (order != null) {
        order.add(marker);
      }
      return Result.success(null);
    };
  }
}
