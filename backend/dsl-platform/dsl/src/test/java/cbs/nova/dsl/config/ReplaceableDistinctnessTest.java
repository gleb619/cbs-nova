package cbs.nova.dsl.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.SingletonSupport.Replaceable;
import cbs.nova.dsl.config.SingletonSupport.Scope;
import cbs.nova.dsl.config.SingletonSupport.SingletonScope;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReplaceableDistinctnessTest {

  @Test
  void replaceablesFromDifferentMethodsAreIndependent() {
    var support = new SingletonSupport() {
      private final Scope scope = SingletonScope.of();

      @Override
      public Scope getScope() {
        return scope;
      }

      public Replaceable<String> a() {
        return replaceable();
      }

      public Replaceable<String> b() {
        return replaceable();
      }
    };

    var a = support.a();
    var b = support.b();

    a.replace("A");
    b.replace("B");

    assertThat(a.get()).isEqualTo("A");
    assertThat(b.get()).isEqualTo("B");
  }

  @Test
  void replacePropagatesImmediatelyToConcurrentReaders() throws Exception {
    var support = new SingletonSupport() {
      private final Scope scope = SingletonScope.of();

      @Override
      public Scope getScope() {
        return scope;
      }

      public Replaceable<String> r() {
        return replaceable("x");
      }
    };

    Replaceable<String> r = support.r();
    int threads = Runtime.getRuntime().availableProcessors() * 2;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      CountDownLatch done = new CountDownLatch(threads);
      AtomicReference<String> firstObserved = new AtomicReference<>();
      r.replace("initial");
      for (int i = 0; i < threads; i++) {
        pool.submit(() -> {
          try {
            String seen = r.get();
            firstObserved.compareAndSet(null, seen);
          } finally {
            done.countDown();
          }
        });
      }
      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(firstObserved.get()).isEqualTo("initial");
      r.replace("replaced");
      assertThat(r.get()).isEqualTo("replaced");
    } finally {
      pool.shutdownNow();
    }
  }
}
