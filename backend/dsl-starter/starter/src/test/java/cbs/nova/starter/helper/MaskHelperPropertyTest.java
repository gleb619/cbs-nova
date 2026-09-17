package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.starter.helper.model.MaskIn;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

class MaskHelperPropertyTest {

  private final MaskHelper helper = new MaskHelper();

  /**
   * Property 1: for any value and any keepFirst/keepLast, the masked output never contains a
   * 2+-character substring of the hidden middle region. Values are generated with all 2-grams
   * unique (lowercase alphabet), so a hidden substring can only recur at its original position —
   * i.e. only if the value leaked through the mask unredacted.
   */
  @Property(tries = 1000)
  void edgesMaskingNeverLeaksHiddenMiddle(
          @ForAll("uniqueBigramStrings") String value,
          @ForAll @IntRange(min = 0, max = 15) int keepFirst,
          @ForAll @IntRange(min = 0, max = 15) int keepLast) {
    String masked = mask(new MaskIn(value, "edges", keepFirst, keepLast, null, null));

    int length = value.length();
    int effectiveFirst = keepFirst;
    int effectiveLast = keepLast;
    // replicate the helper's clamp rule (at least one code point stays masked)
    if (effectiveFirst + effectiveLast >= length) {
      if (effectiveFirst >= length) {
        effectiveFirst = length - 1;
        effectiveLast = 0;
      } else {
        effectiveLast = length - effectiveFirst - 1;
      }
    }
    String hidden = value.substring(effectiveFirst, length - effectiveLast);
    for (int i = 0; i + 2 <= hidden.length(); i++) {
      assertThat(masked).doesNotContain(hidden.substring(i, i + 2));
    }
  }

  /**
   * Property 2: mode="fixed" output length is constant regardless of the input length.
   */
  @Property(tries = 1000)
  void fixedModeOutputLengthIsIndependentOfInputLength(
          @ForAll @StringLength(min = 1, max = 64) String value,
          @ForAll @IntRange(min = 1, max = 16) int width) {
    String masked = mask(new MaskIn(value, "fixed", null, null, null, width));
    assertThat(masked).hasSize(width);
    assertThat(mask(new MaskIn("x", "fixed", null, null, null, width))).isEqualTo(masked);
  }

  /**
   * Property 3: the plain/default overload's output length does not vary with the input length for
   * inputs below the keep-last-4 threshold (no length leak).
   */
  @Property(tries = 1000)
  void defaultMaskingLeaksNoLengthBelowThreshold(
          @ForAll @StringLength(min = 1, max = 7) String value) {
    assertThat(mask(new MaskIn(value, null, null, null, null, null))).isEqualTo("********");
  }

  @Provide
  Arbitrary<String> uniqueBigramStrings() {
    return Arbitraries.randomValue(random -> {
      int length = 3 + random.nextInt(18);
      StringBuilder sb = new StringBuilder();
      while (sb.length() < length) {
        char c = (char) ('a' + random.nextInt(26));
        if (sb.length() > 0) {
          String bigram = sb.substring(sb.length() - 1) + c;
          boolean clash = false;
          for (int i = 0; i + 2 <= sb.length(); i++) {
            if (sb.substring(i, i + 2).equals(bigram)) {
              clash = true;
              break;
            }
          }
          if (clash) {
            continue;
          }
        }
        sb.append(c);
      }
      return sb.toString();
    });
  }

  private String mask(MaskIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx).value().result();
  }
}
