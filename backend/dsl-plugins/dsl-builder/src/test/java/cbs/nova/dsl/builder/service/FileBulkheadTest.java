package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileBulkheadTest {

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    executor = Executors.newFixedThreadPool(4);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void acquireReadAndReleaseReplenishesPermit() {
    var bulkhead = new FileBulkhead(new Semaphore(1), new Semaphore(1), 5L);

    assertThatCode(bulkhead::acquireRead).doesNotThrowAnyException();
    bulkhead.releaseRead();
    assertThatCode(bulkhead::acquireRead).doesNotThrowAnyException();
    bulkhead.releaseRead();
  }

  @Test
  void acquireWriteAndReleaseReplenishesPermit() {
    var bulkhead = new FileBulkhead(new Semaphore(1), new Semaphore(1), 5L);

    assertThatCode(bulkhead::acquireWrite).doesNotThrowAnyException();
    bulkhead.releaseWrite();
    assertThatCode(bulkhead::acquireWrite).doesNotThrowAnyException();
    bulkhead.releaseWrite();
  }

  @Test
  void acquireReadFailsImmediatelyWhenSaturatedAndTimeoutZero() {
    Semaphore read = new Semaphore(1);
    read.tryAcquire();
    var bulkhead = new FileBulkhead(read, new Semaphore(1), 0L);

    assertThatThrownBy(bulkhead::acquireRead)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("file read bulkhead saturated");
  }

  @Test
  void acquireWriteFailsImmediatelyWhenSaturatedAndTimeoutZero() {
    Semaphore write = new Semaphore(1);
    write.tryAcquire();
    var bulkhead = new FileBulkhead(new Semaphore(1), write, 0L);

    assertThatThrownBy(bulkhead::acquireWrite)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("file write bulkhead saturated");
  }

  @Test
  void releaseReadReAdmitsBlockedAcquire() throws Exception {
    Semaphore read = new Semaphore(1);
    read.tryAcquire(); // start saturated
    var bulkhead = new FileBulkhead(read, new Semaphore(1), 5L);

    var blocker = new CountDownLatch(1);
    var proceeded = new CountDownLatch(1);
    Future<?> waiter = executor.submit(() -> {
      try {
        bulkhead.acquireRead();
        proceeded.countDown();
      } finally {
        blocker.countDown();
      }
    });

    // Give the waiter time to actually call tryAcquire (which will block for 5s).
    Thread.sleep(100);
    assertThat(proceeded.getCount()).isEqualTo(1);

    bulkhead.releaseRead();

    assertThat(proceeded.await(5, TimeUnit.SECONDS)).isTrue();
    blocker.await(5, TimeUnit.SECONDS);
    waiter.get(5, TimeUnit.SECONDS);
  }

  @Test
  void releaseWriteReAdmitsBlockedAcquire() throws Exception {
    Semaphore write = new Semaphore(1);
    write.tryAcquire();
    var bulkhead = new FileBulkhead(new Semaphore(1), write, 5L);

    var blocker = new CountDownLatch(1);
    var proceeded = new CountDownLatch(1);
    Future<?> waiter = executor.submit(() -> {
      try {
        bulkhead.acquireWrite();
        proceeded.countDown();
      } finally {
        blocker.countDown();
      }
    });

    Thread.sleep(100);
    assertThat(proceeded.getCount()).isEqualTo(1);

    bulkhead.releaseWrite();

    assertThat(proceeded.await(5, TimeUnit.SECONDS)).isTrue();
    blocker.await(5, TimeUnit.SECONDS);
    waiter.get(5, TimeUnit.SECONDS);
  }

  @Test
  void readAndWriteBulkheadsAreIndependent() {
    Semaphore read = new Semaphore(0);
    Semaphore write = new Semaphore(0);
    var bulkhead = new FileBulkhead(read, write, 0L);

    assertThatThrownBy(bulkhead::acquireRead)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("read");
    // Write still free to attempt (will fail too because no permit).
    assertThatThrownBy(bulkhead::acquireWrite)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("write");
  }

  @Test
  void concurrentAcquireReleasePreservesPermitBalance() throws Exception {
    int permits = 3;
    Semaphore read = new Semaphore(permits);
    var bulkhead = new FileBulkhead(read, new Semaphore(permits), 5L);

    int iterations = 200;
    var failures = new AtomicInteger();
    var ready = new CountDownLatch(8);
    var go = new CountDownLatch(1);
    var done = new CountDownLatch(8);

    for (int i = 0; i < 8; i++) {
      executor.submit(() -> {
        try {
          go.await(5, TimeUnit.SECONDS);
          for (int j = 0; j < iterations; j++) {
            try {
              bulkhead.acquireRead();
              bulkhead.releaseRead();
            } catch (IllegalStateException e) {
              failures.incrementAndGet();
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
      ready.countDown();
    }

    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
    go.countDown();
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

    assertThat(failures.get()).isZero();
    assertThat(read.availablePermits()).isEqualTo(permits);
  }
}
