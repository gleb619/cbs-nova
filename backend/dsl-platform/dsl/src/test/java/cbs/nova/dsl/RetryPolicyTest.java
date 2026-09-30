package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.config.RetryPolicyFactory;
import cbs.nova.dsl.model.RetryPolicy;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  private final RetryPolicyFactory retryPolicyFactory = new RetryPolicyFactory();

  @Test
  void defaultsHaveCorrectValues() {
    var policy = retryPolicyFactory.defaults();
    assertThat(policy.maxAttempts()).isEqualTo(3);
    assertThat(policy.initialInterval()).isEqualTo(Duration.ofSeconds(1));
    assertThat(policy.backoffCoefficient()).isEqualTo(2.0);
    assertThat(policy.maximumInterval()).isNull();
    assertThat(policy.nonRetryable()).isNull();
  }

  @Test
  void customPolicyRetainsValues() {
    var policy = new RetryPolicy(5, Duration.ofMillis(500), 1.5);
    assertThat(policy.maxAttempts()).isEqualTo(5);
    assertThat(policy.initialInterval()).isEqualTo(Duration.ofMillis(500));
    assertThat(policy.backoffCoefficient()).isEqualTo(1.5);
    assertThat(policy.maximumInterval()).isNull();
    assertThat(policy.nonRetryable()).isNull();
  }

  @Test
  void twoDefaultsAreEqual() {
    assertThat(retryPolicyFactory.defaults())
            .isEqualTo(retryPolicyFactory.defaults());
  }

  @Test
  void threeArgAndFiveArgConstructorsAreEqualWhenOptionalsAreNull() {
    var compact = new RetryPolicy(5, Duration.ofMillis(500), 1.5);
    var expanded = new RetryPolicy(5, Duration.ofMillis(500), 1.5, null, null);
    assertThat(compact).isEqualTo(expanded);
    assertThat(compact.hashCode()).isEqualTo(expanded.hashCode());
  }

  @Test
  void withMaximumIntervalPreservesOtherFields() {
    var base = new RetryPolicy(3, Duration.ofSeconds(1), 2.0);
    var capped = base.withMaximumInterval(Duration.ofSeconds(30));
    assertThat(capped.maxAttempts()).isEqualTo(3);
    assertThat(capped.initialInterval()).isEqualTo(Duration.ofSeconds(1));
    assertThat(capped.backoffCoefficient()).isEqualTo(2.0);
    assertThat(capped.maximumInterval()).isEqualTo(Duration.ofSeconds(30));
    assertThat(capped.nonRetryable()).isNull();
  }

  @Test
  void withNonRetryableVarargsPreservesOtherFields() {
    var base = new RetryPolicy(3, Duration.ofSeconds(1), 2.0);
    var withErrors = base.withNonRetryable("ValidationException", "BadRequest");
    assertThat(withErrors.maxAttempts()).isEqualTo(3);
    assertThat(withErrors.initialInterval()).isEqualTo(Duration.ofSeconds(1));
    assertThat(withErrors.backoffCoefficient()).isEqualTo(2.0);
    assertThat(withErrors.maximumInterval()).isNull();
    assertThat(withErrors.nonRetryable()).containsExactly("ValidationException", "BadRequest");
  }

  @Test
  void withNonRetryableListPreservesOtherFields() {
    var base = new RetryPolicy(3, Duration.ofSeconds(1), 2.0);
    var withErrors = base.withNonRetryable(List.of("ValidationException"));
    assertThat(withErrors.nonRetryable()).containsExactly("ValidationException");
    assertThat(withErrors.nonRetryable()).isUnmodifiable();
  }

  @Test
  void withNonRetryableNullClearsList() {
    var base = new RetryPolicy(3, Duration.ofSeconds(1), 2.0,
            null, List.of("X"));
    var cleared = base.withNonRetryable((List<String>) null);
    assertThat(cleared.nonRetryable()).isNull();
  }

  @Test
  void maximumIntervalBelowInitialIntervalRejected() {
    assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(10), 2.0,
            Duration.ofSeconds(1), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maximumInterval")
            .hasMessageContaining("initialInterval");
  }

  @Test
  void maximumIntervalEqualToInitialIntervalAllowed() {
    var policy = new RetryPolicy(3, Duration.ofSeconds(5), 2.0,
            Duration.ofSeconds(5), null);
    assertThat(policy.maximumInterval()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void blankNonRetryableNameRejected() {
    assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(1), 2.0,
            null, List.of("")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-blank");
  }

  @Test
  void nullNonRetryableElementRejected() {
    var list = new java.util.ArrayList<String>();
    list.add(null);
    assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(1), 2.0,
            null, list))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-blank");
  }

  @Test
  void nonPositiveMaxAttemptsRejected() {
    assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofSeconds(1), 2.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAttempts");
  }

  @Test
  void nonPositiveBackoffCoefficientRejected() {
    assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(1), 0.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("backoffCoefficient");
  }
}
