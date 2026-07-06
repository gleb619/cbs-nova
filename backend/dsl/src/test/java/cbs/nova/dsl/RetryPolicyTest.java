package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class RetryPolicyTest {

  @Test
  void defaultsHaveCorrectValues() {
    var policy = RetryPolicy.defaults();
    assertThat(policy.maxAttempts()).isEqualTo(3);
    assertThat(policy.initialInterval()).isEqualTo(Duration.ofSeconds(1));
    assertThat(policy.backoffCoefficient()).isEqualTo(2.0);
  }

  @Test
  void customPolicyRetainsValues() {
    var policy = new RetryPolicy(5, Duration.ofMillis(500), 1.5);
    assertThat(policy.maxAttempts()).isEqualTo(5);
    assertThat(policy.initialInterval()).isEqualTo(Duration.ofMillis(500));
    assertThat(policy.backoffCoefficient()).isEqualTo(1.5);
  }

  @Test
  void twoDefaultsAreEqual() {
    assertThat(RetryPolicy.defaults()).isEqualTo(RetryPolicy.defaults());
  }
}
