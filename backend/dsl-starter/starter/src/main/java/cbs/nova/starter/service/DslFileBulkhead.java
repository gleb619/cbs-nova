package cbs.nova.starter.service;

import cbs.nova.starter.config.properties.DslProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class DslFileBulkhead {

  // TODO: redo to use a some `DslProperties` property instead of hardcode
  private static final long ACQUIRE_TIMEOUT_SECONDS = 5L;

  private final Semaphore readSemaphore;
  private final Semaphore writeSemaphore;

  public void acquireRead() {
    try {
      if (!readSemaphore.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
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
      if (!writeSemaphore.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
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
