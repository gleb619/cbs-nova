package cbs.nova.starter.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BuilderBulkheadTest {

  @Test
  void executeRunsCallAndReleasesPermit() throws Exception {
    var bulkhead = new BuilderBulkhead(new Semaphore(2), 5);

    assertThat(bulkhead.execute(() -> "ok")).isEqualTo("ok");
    assertThat(bulkhead.execute(() -> "again")).isEqualTo("again");
  }

  @Test
  void saturatedBulkheadRejectsAfterTimeout() throws Exception {
    var bulkhead = new BuilderBulkhead(new Semaphore(1), 1);
    bulkhead.acquire();
    try {
      long start = System.nanoTime();
      assertThatThrownBy(bulkhead::acquire).isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("saturated");
      assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isGreaterThanOrEqualTo(
              900);
    } finally {
      bulkhead.release();
    }
    assertThat(bulkhead.execute(() -> "ok")).isEqualTo("ok");
  }

  @Test
  void concurrentCallsRespectPermitLimit() throws Exception {
    var bulkhead = new BuilderBulkhead(new Semaphore(1), 1);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var blocked = executor.submit(() -> bulkhead.execute(() -> {
        entered.countDown();
        release.await(60, TimeUnit.SECONDS);
        return "first";
      }));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> bulkhead.execute(() -> "second"))
              .isInstanceOf(IllegalStateException.class);
      release.countDown();
      assertThat(blocked.get(10, TimeUnit.SECONDS)).isEqualTo("first");
    } finally {
      executor.shutdownNow();
    }
  }

}
