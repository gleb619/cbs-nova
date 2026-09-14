package cbs.nova.starter.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Resilience4j semaphore bulkhead configuration used by {@link DslBuilderClient}.
 */
class BuilderBulkheadTest {

  @Test
  void executeRunsCallAndReleasesPermit() throws Exception {
    var bulkhead = bulkhead(2, 5);
    try {
      assertThat(bulkhead.executeCallable(() -> "ok")).isEqualTo("ok");
      assertThat(bulkhead.executeCallable(() -> "again")).isEqualTo("again");
    } finally {

    }
  }

  @Test
  void saturatedBulkheadRejectsAfterTimeout() throws Exception {
    var bulkhead = bulkhead(1, 1);
    bulkhead.acquirePermission();
    try {
      long start = System.nanoTime();
      assertThatThrownBy(bulkhead::acquirePermission).isInstanceOf(BulkheadFullException.class)
              .hasMessageContaining("Bulkhead");
      assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isGreaterThanOrEqualTo(
              900);
    } finally {
      bulkhead.releasePermission();

    }
    assertThat(bulkhead.executeCallable(() -> "ok")).isEqualTo("ok");
  }

  @Test
  void concurrentCallsRespectPermitLimit() throws Exception {
    var bulkhead = bulkhead(1, 1);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var blocked = executor.submit(() -> bulkhead.executeCallable(() -> {
        entered.countDown();
        release.await(60, TimeUnit.SECONDS);
        return "first";
      }));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> bulkhead.executeCallable(() -> "second"))
              .isInstanceOf(BulkheadFullException.class);
      release.countDown();
      assertThat(blocked.get(10, TimeUnit.SECONDS)).isEqualTo("first");
    } finally {
      executor.shutdownNow();

    }
  }

  private static Bulkhead bulkhead(int permits, long acquireTimeoutSeconds) {
    return Bulkhead.of("test-bulkhead", BulkheadConfig.custom()
            .maxConcurrentCalls(Math.max(1, permits))
            .maxWaitDuration(Duration.ofSeconds(acquireTimeoutSeconds))
            .build());
  }

}
