package cbs.nova.dsl;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.transaction.CompensationRegistry;
import cbs.nova.dsl.transaction.TransactionDslObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

class CompensationRegistryTest {

  private final ContextFactory contextFactory = new ContextFactory();

  private CompensationRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DefaultCompensationRegistry();
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
    registry.compensate("Tx", "run-1", error, contextFactory);

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

    registry.compensate("SameName", "run-1", error, contextFactory);
    assertThat(order).containsExactly("second");
    assertThat(registry.hasCompensation("run-1")).isTrue();

    registry.compensate("SameName", "run-1", error, contextFactory);
    assertThat(order).containsExactly("second", "first");
    assertThat(registry.hasCompensation("run-1")).isFalse();
  }

  @Test
  void compensateIsNoOpForUnknownRunId() {
    registry.compensate("Tx", "unknown-run", new RuntimeException("boom"), contextFactory);
    assertThat(registry.hasCompensation("unknown-run")).isFalse();
  }

  @Test
  void compensateIsNoOpForUnknownTransactionName() {
    var order = new ArrayList<String>();
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var tx = tx("TxA", marker("TxA", order));

    registry.register("TxA", "run-1", ctx, tx);
    registry.compensate("TxB", "run-1", new RuntimeException("boom"), contextFactory);

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

    registry.compensateAll("run-1", error, contextFactory);

    assertThat(order).containsExactly("T3", "T2", "T1");
    assertThat(captured.get()).isSameAs(error);
    assertThat(registry.hasCompensation("run-1")).isFalse();
  }

  @Test
  void compensateAllIsNoOpForUnknownRunId() {
    registry.compensateAll("unknown-run", new RuntimeException("boom"), contextFactory);
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

    registry.compensate("T1", "run-1", new RuntimeException("boom"), contextFactory);
    assertThat(order1).containsExactly("T1");
    assertThat(order2).isEmpty();
    assertThat(registry.hasCompensation("run-1")).isFalse();
    assertThat(registry.hasCompensation("run-2")).isTrue();

    registry.compensateAll("run-2", new RuntimeException("all-boom"), contextFactory);
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

  @Test
  void concurrentRegisterAndCompensateAllIsSafeLifoAndExactlyOnce() throws InterruptedException {
    int runCount = 5;
    int entriesPerRun = 50;
    int registerThreads = 4;
    int compensateThreads = 4;

    @SuppressWarnings("unchecked")
    List<String>[] registeredOrder = new List[runCount];
    @SuppressWarnings("unchecked")
    List<String>[] executedOrder = new List[runCount];
    for (int i = 0; i < runCount; i++) {
      registeredOrder[i] = Collections.synchronizedList(new ArrayList<>());
      executedOrder[i] = Collections.synchronizedList(new ArrayList<>());
    }

    var firedMarkers = new ConcurrentHashMap<String, AtomicInteger>();

    CountDownLatch registeredLatch = new CountDownLatch(runCount * entriesPerRun);
    ExecutorService registerPool = Executors.newFixedThreadPool(registerThreads);
    for (int run = 0; run < runCount; run++) {
      for (int entry = 0; entry < entriesPerRun; entry++) {
        final int runIndex = run;
        final int entryIndex = entry;
        registerPool.submit(() -> {
          var runId = "concurrent-run-" + runIndex;
          var marker = "R" + runIndex + "-T" + entryIndex;
          var ctx = contextFactory.of("body", ExecutionMode.RUN, runId);
          var tx = tx(marker, _ctx -> {
            executedOrder[runIndex].add(marker);
            firedMarkers.computeIfAbsent(marker, _ -> new AtomicInteger(0)).incrementAndGet();
            return Result.success(null);
          });
          synchronized (registeredOrder[runIndex]) {
            registeredOrder[runIndex].add(marker);
            registry.register(marker, runId, ctx, tx);
          }
          registeredLatch.countDown();
        });
      }
    }
    assertThat(registeredLatch.await(10, TimeUnit.SECONDS)).isTrue();

    CountDownLatch compensatedLatch = new CountDownLatch(compensateThreads);
    ExecutorService compensatePool = Executors.newFixedThreadPool(compensateThreads);
    for (int i = 0; i < compensateThreads; i++) {
      compensatePool.submit(() -> {
        try {
          for (int run = 0; run < runCount; run++) {
            registry.compensateAll("concurrent-run-" + run, new RuntimeException("boom"),
                    contextFactory);
          }
        } finally {
          compensatedLatch.countDown();
        }
      });
    }
    assertThat(compensatedLatch.await(10, TimeUnit.SECONDS)).isTrue();

    for (int run = 0; run < runCount; run++) {
      var expected = new ArrayList<>(registeredOrder[run]);
      Collections.reverse(expected);
      assertThat(executedOrder[run]).containsExactlyElementsOf(expected);
      assertThat(registry.hasCompensation("concurrent-run-" + run)).isFalse();
    }

    assertThat(firedMarkers).hasSize(runCount * entriesPerRun);
    firedMarkers.values().forEach(counter -> assertThat(counter.get()).isEqualTo(1));

    registerPool.shutdown();
    compensatePool.shutdown();
    assertThat(registerPool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    assertThat(compensatePool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
  }
}
