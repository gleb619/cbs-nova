package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the parsing contract of {@link FrontmatterNameParser}: doc names come from the first {@code
 * name:} key inside the YAML frontmatter block delimited by {@code ---}.
 */
class FrontmatterNameParserTest {

  @Test
  void returnsNullForNullContent() {
    assertThat(FrontmatterNameParser.name(null)).isNull();
  }

  @Test
  void returnsNullForEmptyContent() {
    assertThat(FrontmatterNameParser.name("")).isNull();
  }

  @Test
  void returnsNullWhenFirstLineIsNotDelimiter() {
    assertThat(FrontmatterNameParser.name("name: foo\n---\n")).isNull();
  }

  @Test
  void returnsNullWhenDelimiterIsNotOnFirstLine() {
    assertThat(FrontmatterNameParser.name("intro\n---\nname: foo\n---\n")).isNull();
  }

  @Test
  void readsPlainNameValue() {
    assertThat(FrontmatterNameParser.name("---\nname: foo\n---\n")).isEqualTo("foo");
  }

  @Test
  void readsNameValueWithoutClosingDelimiter() {
    assertThat(FrontmatterNameParser.name("---\nname: foo")).isEqualTo("foo");
  }

  @Test
  void stripsDoubleQuotes() {
    assertThat(FrontmatterNameParser.name("---\nname: \"foo\"\n---\n")).isEqualTo("foo");
  }

  @Test
  void stripsSingleQuotes() {
    assertThat(FrontmatterNameParser.name("---\nname: 'foo'\n---\n")).isEqualTo("foo");
  }

  @Test
  void keepsMismatchedQuotes() {
    assertThat(FrontmatterNameParser.name("---\nname: \"foo'\n---\n")).isEqualTo("\"foo'");
    assertThat(FrontmatterNameParser.name("---\nname: 'foo\"\n---\n")).isEqualTo("'foo\"");
  }

  @Test
  void unquotesEmptyQuotedValue() {
    assertThat(FrontmatterNameParser.name("---\nname: \"\"\n---\n")).isEmpty();
    assertThat(FrontmatterNameParser.name("---\nname: ''\n---\n")).isEmpty();
  }

  @Test
  void keepsSingleCharQuotedValueUnchanged() {
    assertThat(FrontmatterNameParser.name("---\nname: \"\n---\n")).isEqualTo("\"");
    assertThat(FrontmatterNameParser.name("---\nname: '\n---\n")).isEqualTo("'");
  }

  @Test
  void handlesCrlfLineEndings() {
    assertThat(FrontmatterNameParser.name("---\r\nname: foo\r\n---\r\n")).isEqualTo("foo");
  }

  @Test
  void handlesLfLineEndings() {
    assertThat(FrontmatterNameParser.name("---\nname: foo\n---\n")).isEqualTo("foo");
  }

  @Test
  void toleratesWhitespaceAroundDelimiterKeyAndColon() {
    assertThat(FrontmatterNameParser.name("  ---  \n   name   :   foo   \n  ---  \n"))
            .isEqualTo("foo");
  }

  @Test
  void readsNameAfterOtherKeys() {
    assertThat(FrontmatterNameParser.name("---\nid: 42\nname: foo\ntitle: bar\n---\n"))
            .isEqualTo("foo");
  }

  @Test
  void returnsFirstNameWhenRepeated() {
    assertThat(FrontmatterNameParser.name("---\nname: first\nname: second\n---\n"))
            .isEqualTo("first");
  }

  @Test
  void returnsNullWhenClosingDelimiterPrecedesName() {
    assertThat(FrontmatterNameParser.name("---\nid: 42\n---\nname: foo\n")).isNull();
  }

  @Test
  void returnsNullForNameWithEmptyValue() {
    assertThat(FrontmatterNameParser.name("---\nname:\n---\n")).isNull();
  }

  @Test
  void returnsNullWhenNameKeyIsAbsent() {
    assertThat(FrontmatterNameParser.name("---\nid: 42\n---\n")).isNull();
  }

  @Test
  void doesNotMatchSimilarKeys() {
    assertThat(FrontmatterNameParser.name("---\nfilename: foo\n---\n")).isNull();
    assertThat(FrontmatterNameParser.name("---\nmy_name: foo\n---\n")).isNull();
  }

  // Probes pinning observed behaviour; production code unchanged.

  @Test
  void probeLeadingBomBeforeDelimiterYieldsNull() {
    assertThat(FrontmatterNameParser.name("\uFEFF---\nname: foo\n---\n")).isNull();
  }

  @Test
  void probeTrailingInlineCommentIsKeptAsPartOfValue() {
    assertThat(FrontmatterNameParser.name("---\nname: foo # comment\n---\n"))
            .isEqualTo("foo # comment");
  }

  @Test
  void probeIndentedNameKeyIsStillMatched() {
    assertThat(FrontmatterNameParser.name("---\nnested:\n  name: foo\n---\n")).isEqualTo("foo");
  }
}
