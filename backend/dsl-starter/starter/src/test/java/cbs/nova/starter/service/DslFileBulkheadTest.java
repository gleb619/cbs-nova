package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;

class DslFileBulkheadTest {

  @Test
  void acquireReadSaturatesWhenAllPermitsHeld() {
    DslFileBulkhead bulkhead = new DslFileBulkhead(new Semaphore(1), new Semaphore(1), 1L);

    bulkhead.acquireRead();

    long start = System.nanoTime();
    assertThatThrownBy(bulkhead::acquireRead)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("file read bulkhead saturated");
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

    assertThat(elapsedMillis).isLessThan(5000L);
  }
}
