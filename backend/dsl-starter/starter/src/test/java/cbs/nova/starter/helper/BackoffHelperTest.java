package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.BackoffIn;
import cbs.nova.starter.helper.model.BackoffOut;
import org.junit.jupiter.api.Test;

class BackoffHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final BackoffHelper helper = new BackoffHelper();

  @Test
  void computesNoneTable() {
    long[] expected = {1000, 2000, 4000, 8000, 16000, 32000};
    for (int attempt = 0; attempt < expected.length; attempt++) {
      assertThat(delay(new BackoffIn(attempt, 1000L, 60000L, "none", null)))
              .isEqualTo(expected[attempt]);
    }
    assertThat(delay(new BackoffIn(6, 1000L, 60000L, "none", null))).isEqualTo(60000);
  }

  @Test
  void computesJitterBands() {
    for (int i = 0; i < 200; i++) {
      assertThat(delay(new BackoffIn(4, 1000L, 60000L, "full", null)))
              .isBetween(0L, 16000L);
      assertThat(delay(new BackoffIn(4, 1000L, 60000L, "equal", null)))
              .isBetween(8000L, 16000L);
      assertThat(delay(new BackoffIn(1, 1000L, 60000L, "decorrelated", 5000L)))
              .isBetween(1000L, 15000L);
    }
  }

  @Test
  void rejectsInvalidInput() {
    assertThat(failure(new BackoffIn(-1, 1000L, 60000L, "none", null)))
            .isInstanceOf(IllegalArgumentException.class);
    assertThat(failure(new BackoffIn(0, 0L, 60000L, "none", null)))
            .isInstanceOf(IllegalArgumentException.class);
    assertThat(failure(new BackoffIn(0, 100000L, 1000L, "none", null)))
            .isInstanceOf(IllegalArgumentException.class);
    assertThat(failure(new BackoffIn(0, 1000L, 60000L, "wild", null)))
            .isInstanceOf(IllegalArgumentException.class);
    assertThat(failure(new BackoffIn(0, 1000L, 60000L, "decorrelated", 10L)))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isOverflowSafeAndAppliesDefaults() {
    assertThat(delay(new BackoffIn(60, 1000L, 60000L, "none", null))).isLessThanOrEqualTo(60000);
    assertThat(delay(new BackoffIn(0, null, null, "none", null))).isEqualTo(1000);
  }

  private long delay(BackoffIn input) {
    Result<BackoffOut> result = helper.execute(contextFactory.of(input, ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isTrue();
    return result.value().delayMillis();
  }

  private Throwable failure(BackoffIn input) {
    Result<BackoffOut> result = helper.execute(contextFactory.of(input, ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
    return result.cause();
  }
}
