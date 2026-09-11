package cbs.nova.dsl.builder.service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FileBulkhead {

  private final Semaphore readSemaphore;
  private final Semaphore writeSemaphore;
  private final long acquireTimeoutSeconds;

  public void acquireRead() {
    try {
      if (!readSemaphore.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS)) {
        throw new IllegalStateException("file read bulkhead saturated");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while acquiring read permit", e);
    }
  }

  public void releaseRead() {
    readSemaphore.release();
  }

  public void acquireWrite() {
    try {
      if (!writeSemaphore.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS)) {
        throw new IllegalStateException("file write bulkhead saturated");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while acquiring write permit", e);
    }
  }

  public void releaseWrite() {
    writeSemaphore.release();
  }
}
