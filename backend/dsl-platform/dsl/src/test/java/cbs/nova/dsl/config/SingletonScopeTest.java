package cbs.nova.dsl.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.SingletonSupport.Factory;
import cbs.nova.dsl.config.SingletonSupport.Kind;
import cbs.nova.dsl.config.SingletonSupport.SingletonScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

class SingletonScopeTest {

  @Test
  void recursiveSingletonFactoryReturnsSingleConsistentInstance() {
    SingletonScope scope = SingletonScope.of();
    Kind kind = new Kind("recursive");
    AtomicInteger creationCount = new AtomicInteger();

    Factory<String> inner = new Factory<>() {
      @Override
      public String get() {
        return "created-" + creationCount.incrementAndGet();
      }

      @Override
      public Kind kind() {
        return kind;
      }
    };

    Factory<String> outer = new Factory<>() {
      @Override
      public String get() {
        return scope.get(inner);
      }

      @Override
      public Kind kind() {
        return kind;
      }
    };

    String first = scope.get(outer);
    String second = scope.get(outer);

    assertThat(first).isNotNull().startsWith("created-");
    assertThat(second).isSameAs(first);
  }

  @Test
  void recursiveSingletonFactoryIsConsistentUnderConcurrentAccess() throws Exception {
    SingletonScope scope = SingletonScope.of();
    Kind kind = new Kind("concurrent-recursive");
    AtomicInteger creationCount = new AtomicInteger();
    List<String> observed = new CopyOnWriteArrayList<>();

    Factory<String> inner = new Factory<>() {
      @Override
      public String get() {
        return "concurrent-" + creationCount.incrementAndGet();
      }

      @Override
      public Kind kind() {
        return kind;
      }
    };

    Factory<String> outer = new Factory<>() {
      @Override
      public String get() {
        return scope.get(inner);
      }

      @Override
      public Kind kind() {
        return kind;
      }
    };

    int threads = Runtime.getRuntime().availableProcessors() * 2;
    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      Callable<String> task = () -> {
        String value = scope.get(outer);
        observed.add(value);
        return value;
      };
      List<Future<String>> futures = executor.invokeAll(
              java.util.Collections.nCopies(threads, task));

      String winner = null;
      for (Future<String> future : futures) {
        String value = future.get();
        if (winner == null) {
          winner = value;
        }
        assertThat(value).isSameAs(winner);
      }
    }

    assertThat(observed).isNotEmpty();
    assertThat(new java.util.HashSet<>(observed)).hasSize(1);
  }
}
