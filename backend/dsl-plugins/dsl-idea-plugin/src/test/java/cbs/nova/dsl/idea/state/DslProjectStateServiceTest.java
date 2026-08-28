package cbs.nova.dsl.idea.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class DslProjectStateServiceTest extends BasePlatformTestCase {

  public void testFreshProjectIsNotActiveDslProject() {
    var service = DslProjectStateService.getInstance(getProject());

    assertThat(service.isActiveDslProject()).isFalse();
  }

  public void testSetActiveDslProjectRoundTripsTrueAndFalse() {
    var service = DslProjectStateService.getInstance(getProject());

    service.setActiveDslProject(true);
    assertThat(service.isActiveDslProject()).isTrue();

    service.setActiveDslProject(false);
    assertThat(service.isActiveDslProject()).isFalse();
  }

  public void testGetInstanceReturnsSameInstanceForSameProject() {
    var first = DslProjectStateService.getInstance(getProject());
    var second = DslProjectStateService.getInstance(getProject());

    assertThat(second).isSameAs(first);
  }

  public void testFlippingFlagFromFreshStateYieldsPersistedValue() {
    var service = DslProjectStateService.getInstance(getProject());
    assertThat(service.isActiveDslProject()).isFalse();

    service.setActiveDslProject(true);

    assertThat(DslProjectStateService.getInstance(getProject()).isActiveDslProject())
            .isTrue();
  }

  public void testConcurrentSetActiveIsVisibleToConcurrentReaders() throws Exception {
    var service = DslProjectStateService.getInstance(getProject());
    int writers = 4;
    int readers = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
    ExecutorService pool = Executors.newFixedThreadPool(writers + readers);
    try {
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(writers + readers);
      for (int w = 0; w < writers; w++) {
        final boolean target = (w % 2 == 0);
        pool.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < 1_000; i++) {
              service.setActiveDslProject(target);
            }
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        });
      }
      for (int r = 0; r < readers; r++) {
        pool.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < 1_000; i++) {
              service.isActiveDslProject();
            }
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        });
      }
      start.countDown();
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }
  }

  public void testFinalFlagIsSettledAfterConcurrentWriters() throws Exception {
    var service = DslProjectStateService.getInstance(getProject());
    int writers = 4;
    ExecutorService pool = Executors.newFixedThreadPool(writers);
    try {
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(writers);
      for (int w = 0; w < writers; w++) {
        final boolean target = (w == 0);
        pool.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < 500; i++) {
              service.setActiveDslProject(target);
            }
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        });
      }
      start.countDown();
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(service.isActiveDslProject()).isFalse();
    } finally {
      pool.shutdownNow();
    }
  }
}
