package cbs.nova.starter.vhs.scrub;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.vhs.scrub.JsonPathNavigator.Segment;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonPathNavigatorTest {

  // ---- empty / blank / root -------------------------------------------------

  @Test
  void nullReturnsEmpty() {
    assertThat(JsonPathNavigator.parse(null)).isEmpty();
  }

  @Test
  void blankReturnsEmpty() {
    assertThat(JsonPathNavigator.parse("   ")).isEmpty();
  }

  @Test
  void dollarOnlyReturnsEmpty() {
    assertThat(JsonPathNavigator.parse("$")).isEmpty();
  }

  @Test
  void dollarDotReturnsEmpty() {
    assertThat(JsonPathNavigator.parse("$.")).isEmpty();
  }

  // ---- single field ---------------------------------------------------------

  @Test
  void dollarPrefixedField() {
    List<Segment> segments = JsonPathNavigator.parse("$.token");
    assertThat(segments).containsExactly(new Segment("token", false));
  }

  @Test
  void bareField() {
    List<Segment> segments = JsonPathNavigator.parse("token");
    assertThat(segments).containsExactly(new Segment("token", false));
  }

  @Test
  void leadingDotField() {
    List<Segment> segments = JsonPathNavigator.parse(".token");
    assertThat(segments).containsExactly(new Segment("token", false));
  }

  // ---- wildcard -------------------------------------------------------------

  @Test
  void wildcardFieldThenField() {
    List<Segment> segments = JsonPathNavigator.parse("$.accounts[*].iban");
    assertThat(segments).containsExactly(
            new Segment("accounts", true),
            new Segment("iban", false));
  }

  @Test
  void bareBracketIsLenientWildcard() {
    List<Segment> segments = JsonPathNavigator.parse("$.accounts[].iban");
    assertThat(segments).containsExactly(
            new Segment("accounts", true),
            new Segment("iban", false));
  }

  // ---- trimming / immutability --------------------------------------------

  @Test
  void whitespaceIsTrimmed() {
    List<Segment> segments = JsonPathNavigator.parse("   $.token   ");
    assertThat(segments).containsExactly(new Segment("token", false));
  }

  @Test
  void resultListIsImmutable() {
    List<Segment> segments = JsonPathNavigator.parse("$.token");
    org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> segments.clear());
  }

  // ---- malformed-input pinning --------------------------------------------
  // Pin only. Run BEFORE build; if shape looks like scrub bypass, stop and report.

  @Test
  void doubleDotProducesEmptyNameSegment() {
    // Splits to ["a", "", "b"] — current behavior yields a segment with empty name.
    List<Segment> segments = JsonPathNavigator.parse("a..b");
    assertThat(segments).containsExactly(
            new Segment("a", false),
            new Segment("", false),
            new Segment("b", false));
  }

  @Test
  void dollarBracketStarProducesWildcardWithEmptyName() {
    // After stripping '$' → '[*]' → splits to ['[*]'] → name="", wildcard=true.
    List<Segment> segments = JsonPathNavigator.parse("$[*]");
    assertThat(segments).containsExactly(new Segment("", true));
  }

  @Test
  void indexedBracketIsLiteralFieldName() {
    // No [*] / [] suffix, so bracket form is kept as part of the field name.
    List<Segment> segments = JsonPathNavigator.parse("a[0]");
    assertThat(segments).containsExactly(new Segment("a[0]", false));
  }
}
