package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Map;

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

  @Test
  void asConvertsToTypedValue() {
    Result<?> r = Result.success("hello");
    assertThat(r.as(String.class)).isEqualTo("hello");
  }

  @Test
  void asOnFailureReturnsNull() {
    Result<?> r = Result.failure(new RuntimeException("oops"));
    assertThat(r.as(String.class)).isNull();
  }

  @Test
  void asMapWithMapValueReturnsMap() {
    Map<String, Object> map = Map.of("key", "val");
    Result<?> r = Result.success(map);
    assertThat(r.asMap()).containsEntry("key", "val");
  }

  @Test
  void asMapWithNonMapValueWrapsIt() {
    Result<?> r = Result.success("hello");
    assertThat(r.asMap()).containsEntry("value", "hello");
  }

  @Test
  void asMapOnFailureReturnsEmptyMap() {
    Result<?> r = Result.failure(new RuntimeException("oops"));
    assertThat(r.asMap()).isEmpty();
  }
}
