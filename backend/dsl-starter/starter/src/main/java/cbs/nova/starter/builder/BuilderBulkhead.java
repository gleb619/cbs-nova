package cbs.nova.starter.builder;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;

//TODO: replace with a Resilience4j
@RequiredArgsConstructor
public class BuilderBulkhead {

  private final Semaphore semaphore;
  private final long acquireTimeoutSeconds;

  public <T> T execute(Callable<T> call) throws Exception {
    acquire();
    try {
      return call.call();
    } finally {
      semaphore.release();
    }
  }

  public void acquire() {
    try {
      if (!semaphore.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS)) {
        throw new IllegalStateException("builder client bulkhead saturated");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while acquiring builder permit", e);
    }
  }

  public void release() {
    semaphore.release();
  }

}
