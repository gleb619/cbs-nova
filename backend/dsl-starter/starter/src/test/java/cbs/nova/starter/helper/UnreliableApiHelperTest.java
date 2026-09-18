package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.model.UnreliableApiFailurePattern;
import cbs.nova.starter.helper.model.UnreliableApiIn;
import cbs.nova.starter.helper.model.UnreliableApiOut;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class UnreliableApiHelperTest {

  @Test
  void consecutivePatternFailsConfiguredNumberOfTimesThenSucceeds() {
    UnreliableApiHelper helper = defaultHelper();
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
    UnreliableApiHelper helper = defaultHelper();
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
    UnreliableApiHelper helper = helperWithTtl(Duration.ofMillis(50));
    String id = "ttl";
    Result<UnreliableApiOut> first = run(helper, id, 0, false);
    assertThat(first.isSuccess()).isTrue();
    assertThat(helper.attempts()).containsKey(id);

    Thread.sleep(150);
    helper.cleanUp();

    assertThat(helper.attempts()).doesNotContainKey(id);
  }

  @Test
  void resetClearsState() {
    UnreliableApiHelper helper = defaultHelper();
    run(helper, "reset", 0, false);
    helper.reset();
    assertThat(helper.attempts()).isEmpty();
  }

  private static UnreliableApiHelper defaultHelper() {
    return helperWithTtl(StarterConstants.UNRELIABLE_API_TTL);
  }

  private static UnreliableApiHelper helperWithTtl(Duration ttl) {
    return new UnreliableApiHelper(Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(StarterConstants.UNRELIABLE_API_MAX_SIZE)
            .build());
  }

  private Result<UnreliableApiOut> run(UnreliableApiHelper helper, String id, int failCount,
          boolean jitter) {
    return run(helper, id, failCount, jitter, null);
  }

  @Test
  void descriptionIsNonEmptyMarkdown() {
    UnreliableApiHelper helper = defaultHelper();
    String desc = helper.description();

    assertThat(desc).isNotBlank()
            .contains("unreliable")
            .contains("CONSECUTIVE")
            .contains("RANDOM")
            .contains("```mermaid");
  }

  private Result<UnreliableApiOut> run(UnreliableApiHelper helper, String id, int failCount,
          boolean jitter, UnreliableApiFailurePattern pattern) {
    var ctx = SimpleContext.<UnreliableApiIn>builder()
            .body(new UnreliableApiIn(id, failCount, jitter, null,
                    pattern == null ? null : pattern.name()))
            .mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
