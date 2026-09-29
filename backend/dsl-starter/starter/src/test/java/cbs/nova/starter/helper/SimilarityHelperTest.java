package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.SimilarityIn;
import cbs.nova.starter.helper.model.SimilarityOut;
import org.junit.jupiter.api.Test;

class SimilarityHelperTest {

  private static final double TOLERANCE = 1.0e-6;

  private final SimilarityHelper helper = new SimilarityHelper();

  // --- levenshtein ---------------------------------------------------------

  @Test
  void levenshteinKittenSittingKnownDistance() {
    double score = score("levenshtein", "kitten", "sitting", false);
    assertThat(score).isEqualTo(1.0 - 3.0 / 7.0, within(TOLERANCE));
  }

  @Test
  void levenshteinEqualStrings() {
    assertThat(score("levenshtein", "hello", "hello", false)).isEqualTo(1.0);
  }

  @Test
  void levenshteinCaseInsensitiveEqual() {
    assertThat(score("levenshtein", "Hello", "HELLO", false)).isEqualTo(1.0);
  }

  @Test
  void levenshteinCaseSensitiveDifferent() {
    double score = score("levenshtein", "Hello", "hello", true);
    assertThat(score).isLessThan(1.0);
    assertThat(score).isGreaterThan(0.0);
  }

  @Test
  void levenshteinBothEmpty() {
    assertThat(score("levenshtein", "", "", false)).isEqualTo(1.0);
  }

  @Test
  void levenshteinOneEmpty() {
    assertThat(score("levenshtein", "abc", "", false)).isEqualTo(0.0);
    assertThat(score("levenshtein", "", "abc", false)).isEqualTo(0.0);
  }

  @Test
  void levenshteinCompletelyDifferent() {
    assertThat(score("levenshtein", "abc", "xyz", false)).isEqualTo(0.0);
  }

  @Test
  void levenshteinUnicodeSafe() {
    double score = score("levenshtein", "café", "cafe", false);
    assertThat(score).isBetween(0.0, 1.0);
  }

  // --- jaroWinkler ---------------------------------------------------------

  @Test
  void jaroWinklerMarthaMarhtaKnownValue() {
    double score = score("jaroWinkler", "martha", "marhta", false);
    assertThat(score).isEqualTo(0.961111, within(TOLERANCE));
  }

  @Test
  void jaroWinklerEqualStrings() {
    assertThat(score("jaroWinkler", "hello", "hello", false)).isEqualTo(1.0);
  }

  @Test
  void jaroWinklerCaseInsensitive() {
    double score = score("jaroWinkler", "MARTHA", "marhta", false);
    assertThat(score).isEqualTo(0.961111, within(TOLERANCE));
  }

  @Test
  void jaroWinklerCaseSensitive() {
    double score = score("jaroWinkler", "MARTHA", "marhta", true);
    assertThat(score).isLessThan(0.961);
  }

  @Test
  void jaroWinklerBothEmpty() {
    assertThat(score("jaroWinkler", "", "", false)).isEqualTo(1.0);
  }

  @Test
  void jaroWinklerOneEmpty() {
    assertThat(score("jaroWinkler", "abc", "", false)).isEqualTo(0.0);
  }

  @Test
  void jaroWinklerNoCommonCharacters() {
    assertThat(score("jaroWinkler", "abc", "xyz", false)).isEqualTo(0.0);
  }

  // --- ratio (Dice bigram) ------------------------------------------------

  @Test
  void ratioEqualStrings() {
    assertThat(score("ratio", "hello", "hello", false)).isEqualTo(1.0);
  }

  @Test
  void ratioAllBigramsShared() {
    double score = score("ratio", "abcd", "abcd", false);
    assertThat(score).isEqualTo(1.0);
  }

  @Test
  void ratioBothEmpty() {
    assertThat(score("ratio", "", "", false)).isEqualTo(1.0);
  }

  @Test
  void ratioOneTooShort() {
    assertThat(score("ratio", "a", "abc", false)).isEqualTo(0.0);
    assertThat(score("ratio", "abc", "a", false)).isEqualTo(0.0);
  }

  @Test
  void ratioCaseInsensitive() {
    assertThat(score("ratio", "Hello", "hello", false)).isEqualTo(1.0);
  }

  @Test
  void ratioPartialOverlap() {
    double score = score("ratio", "night", "nacht", false);
    assertThat(score).isBetween(0.0, 1.0);
    assertThat(score).isLessThan(1.0);
  }

  // --- mode dispatch -------------------------------------------------------

  @Test
  void unknownModeFails() {
    Result<SimilarityOut> result = execute(new SimilarityIn("bogus", "a", "b", false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("similarity.mode");
  }

  @Test
  void nullModeFails() {
    Result<SimilarityOut> result = execute(new SimilarityIn(null, "a", "b", false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void modeIsCaseInsensitive() {
    assertThat(score("LEVENSHTEIN", "abc", "abc", false)).isEqualTo(1.0);
    assertThat(score("JarOWinkler", "martha", "marhta", false))
            .isEqualTo(0.961111, within(TOLERANCE));
    assertThat(score("RATIO", "abc", "abc", false)).isEqualTo(1.0);
  }

  // --- null safety ---------------------------------------------------------

  @Test
  void nullAFails() {
    Result<SimilarityOut> result = execute(new SimilarityIn("levenshtein", null, "x", false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("similarity.a");
  }

  @Test
  void nullBFails() {
    Result<SimilarityOut> result = execute(new SimilarityIn("levenshtein", "x", null, false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("similarity.b");
  }

  @Test
  void nullCaseSensitiveDefaultsToFalse() {
    double score = score("levenshtein", "Hello", "HELLO", null);
    assertThat(score).isEqualTo(1.0);
  }

  // --- helpers -------------------------------------------------------------

  private double score(String mode, String a, String b, Boolean caseSensitive) {
    Result<SimilarityOut> result = execute(new SimilarityIn(mode, a, b, caseSensitive));
    assertThat(result.isSuccess())
            .as("mode=%s a=%s b=%s caseSensitive=%s", mode, a, b, caseSensitive)
            .isTrue();
    return result.value().score();
  }

  private Result<SimilarityOut> execute(SimilarityIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
