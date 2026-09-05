package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.RandomIn;
import cbs.nova.starter.helper.model.RandomOut;
import java.util.List;
import org.junit.jupiter.api.Test;

class RandomHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final RandomHelper helper = new RandomHelper();

  // --- int mode ---

  @Test
  void intStaysInInclusiveBounds() {
    for (int i = 0; i < 1000; i++) {
      Integer r = (Integer) intMode(1, 6).value().result();
      assertThat(r).isBetween(1, 6);
    }
  }

  @Test
  void intDegenerateBoundsAlwaysReturnsValue() {
    for (int i = 0; i < 100; i++) {
      Integer r = (Integer) intMode(5, 5).value().result();
      assertThat(r).isEqualTo(5);
    }
  }

  @Test
  void intSmallRangeProducesBothValues() {
    boolean sawOne = false;
    boolean sawTwo = false;
    for (int i = 0; i < 1000 && !(sawOne && sawTwo); i++) {
      Integer r = (Integer) intMode(1, 2).value().result();
      if (r == 1)
        sawOne = true;
      if (r == 2)
        sawTwo = true;
    }
    assertThat(sawOne).isTrue();
    assertThat(sawTwo).isTrue();
  }

  @Test
  void intMinGreaterThanMaxFails() {
    Result<RandomOut> result = execute(
            new RandomIn("int", 10, 1, null, null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("int.min");
  }

  // --- long mode ---

  @Test
  void longStaysInInclusiveBounds() {
    for (int i = 0; i < 1000; i++) {
      Long r = (Long) longMode(0L, 100L).value().result();
      assertThat(r).isBetween(0L, 100L);
    }
  }

  @Test
  void longMinGreaterThanMaxFails() {
    Result<RandomOut> result = execute(
            new RandomIn("long", null, null, 100L, 0L, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- double mode ---

  @Test
  void doubleUnitRangeStaysInHalfOpenBounds() {
    for (int i = 0; i < 1000; i++) {
      Double r = (Double) doubleMode(0.0, 1.0).value().result();
      assertThat(r).isGreaterThanOrEqualTo(0.0).isLessThan(1.0);
    }
  }

  @Test
  void doubleCustomRangeStaysInHalfOpenBounds() {
    for (int i = 0; i < 1000; i++) {
      Double r = (Double) doubleMode(2.5, 7.5).value().result();
      assertThat(r).isGreaterThanOrEqualTo(2.5).isLessThan(7.5);
    }
  }

  @Test
  void doubleMinGreaterThanMaxFails() {
    Result<RandomOut> result = execute(
            new RandomIn("double", null, null, null, null, 5.0, 1.0, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- string mode ---

  @Test
  void stringAlphanumericMatchesPool() {
    String r = (String) stringMode(10, "alphanumeric").value().result();
    assertThat(r).hasSize(10).matches("[a-zA-Z0-9]{10}");
  }

  @Test
  void stringZeroLengthIsEmpty() {
    String r = (String) stringMode(0, "alpha").value().result();
    assertThat(r).isEmpty();
  }

  @Test
  void stringHexMatchesHexPool() {
    String r = (String) stringMode(10, "hex").value().result();
    assertThat(r).hasSize(10).matches("[0-9a-f]{10}");
  }

  @Test
  void stringNegativeLengthFails() {
    Result<RandomOut> result = execute(
            new RandomIn("string", null, null, null, null, null, null, -1, "alpha", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stringLengthAboveLimitFails() {
    Result<RandomOut> result = execute(
            new RandomIn("string", null, null, null, null, null, null, 100001, "alpha", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stringUnknownCharsetFails() {
    Result<RandomOut> result = execute(
            new RandomIn("string", null, null, null, null, null, null, 5, "emoji", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("charset");
  }

  // --- choice mode ---

  @Test
  void choiceAlwaysReturnsMemberOfList() {
    for (int i = 0; i < 200; i++) {
      Object r = choiceMode(List.of(1, 2, 3)).value().result();
      assertThat(r).isIn((Object) 1, (Object) 2, (Object) 3);
    }
  }

  @Test
  void choiceEmptyListFails() {
    Result<RandomOut> result = execute(
            new RandomIn("choice", null, null, null, null, null, null, null, null, List.of()));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void choiceNullListFails() {
    Result<RandomOut> result = execute(
            new RandomIn("choice", null, null, null, null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- unknown mode ---

  @Test
  void unknownModeFails() {
    Result<RandomOut> result = execute(
            new RandomIn("weird", null, null, null, null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("random.mode");
  }

  // --- helpers ---

  private Result<RandomOut> execute(RandomIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }

  private Result<RandomOut> intMode(int min, int max) {
    return execute(new RandomIn("int", min, max, null, null, null, null, null, null, null));
  }

  private Result<RandomOut> longMode(long min, long max) {
    return execute(new RandomIn("long", null, null, min, max, null, null, null, null, null));
  }

  private Result<RandomOut> doubleMode(double min, double max) {
    return execute(new RandomIn("double", null, null, null, null, min, max, null, null, null));
  }

  private Result<RandomOut> stringMode(int length, String charset) {
    return execute(
            new RandomIn("string", null, null, null, null, null, null, length, charset, null));
  }

  private Result<RandomOut> choiceMode(List<Object> list) {
    return execute(new RandomIn("choice", null, null, null, null, null, null, null, null, list));
  }
}
