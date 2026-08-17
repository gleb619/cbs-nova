package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helpers.model.UnreliableApiFailurePattern;
import cbs.nova.starter.helpers.model.UnreliableApiIn;
import cbs.nova.starter.helpers.model.UnreliableApiOut;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class UnreliableApiHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void consecutivePatternFailsConfiguredNumberOfTimesThenSucceeds() {
    UnreliableApiHelper helper = new UnreliableApiHelper();
    String id = "consecutive";

    for (int i = 0; i < 3; i++) {
      Result<UnreliableApiOut> result = run(helper, id, 3, false);
      assertThat(result.isSuccess()).isFalse();
    }
    Result<UnreliableApiOut> success = run(helper, id, 3, false);
    assertThat(success.isSuccess()).isTrue();
    assertThat(success.value().attempts()).isEqualTo(4);
  }

  @Test
  void randomPatternUsesFailCountAsPercentage() {
    UnreliableApiHelper helper = new UnreliableApiHelper();
    String id = "random";
    int failures = 0;
    for (int i = 0; i < 100; i++) {
      Result<UnreliableApiOut> result = run(helper, id, 100, false,
              UnreliableApiFailurePattern.RANDOM);
      if (!result.isSuccess()) {
        failures++;
      }
    }
    assertThat(failures).isEqualTo(100);
  }

  @Test
  void stateIsCleanedUpAfterTtl() throws InterruptedException {
    UnreliableApiHelper helper = new UnreliableApiHelper(Duration.ofMillis(50));
    String id = "ttl";
    Result<UnreliableApiOut> first = run(helper, id, 0, false);
    assertThat(first.isSuccess()).isTrue();
    assertThat(helper.attempts()).containsKey(id);

    Thread.sleep(150);
    helper.evictExpired();

    assertThat(helper.attempts()).doesNotContainKey(id);
  }

  @Test
  void resetClearsStateAndStopsScheduler() {
    UnreliableApiHelper helper = new UnreliableApiHelper();
    run(helper, "reset", 0, false);
    helper.reset();
    assertThat(helper.attempts()).isEmpty();
  }

  private Result<UnreliableApiOut> run(UnreliableApiHelper helper, String id, int failCount,
          boolean jitter) {
    return run(helper, id, failCount, jitter, null);
  }

  private Result<UnreliableApiOut> run(UnreliableApiHelper helper, String id, int failCount,
          boolean jitter, UnreliableApiFailurePattern pattern) {
    var ctx = contextFactory.of(
            new UnreliableApiIn(id, failCount, jitter, null,
                    pattern == null ? null : pattern.name()),
            ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
