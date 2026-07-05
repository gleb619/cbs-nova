package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ResultTest {
  @Test
  void successHoldsValue() {
    var r = Result.success("ok");
    assertThat(r.isSuccess()).isTrue();
    assertThat(r.value()).isEqualTo("ok");
    assertThat(r.cause()).isNull();
  }

  @Test
  void failureHoldsCause() {
    var ex = new RuntimeException("boom");
    var r = Result.failure(ex);
    assertThat(r.isSuccess()).isFalse();
    assertThat(r.cause()).isSameAs(ex);
    assertThat(r.value()).isNull();
  }
}
