package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileBulkhead {

  private final Semaphore readSemaphore;
  private final Semaphore writeSemaphore;
  private final long acquireTimeoutSeconds;

  // TODO: replace constructor init with spring configuration
  @Autowired
  public FileBulkhead(DslBuilderProperties properties) {
    this(new Semaphore(properties.files().readBulkheadPermits()),
            new Semaphore(properties.files().writeBulkheadPermits()),
            properties.files().acquireTimeoutSeconds());
  }

  // TODO: replace with lomboks constructor
  public FileBulkhead(Semaphore readSemaphore, Semaphore writeSemaphore,
          long acquireTimeoutSeconds) {
    this.readSemaphore = readSemaphore;
    this.writeSemaphore = writeSemaphore;
    this.acquireTimeoutSeconds = acquireTimeoutSeconds;
  }

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
