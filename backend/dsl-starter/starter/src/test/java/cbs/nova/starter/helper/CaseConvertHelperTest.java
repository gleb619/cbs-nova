package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.CaseConvertIn;
import cbs.nova.starter.helper.model.CaseConvertOut;
import org.junit.jupiter.api.Test;

class CaseConvertHelperTest {

  private final CaseConvertHelper helper = new CaseConvertHelper();

  @Test
  void camelFromSnake() {
    assertThat(convert("foo_bar", "camel")).isEqualTo("fooBar");
  }

  @Test
  void camelFromKebab() {
    assertThat(convert("foo-bar-baz", "camel")).isEqualTo("fooBarBaz");
  }

  @Test
  void camelFromSpaceSeparated() {
    assertThat(convert("foo bar", "camel")).isEqualTo("fooBar");
  }

  @Test
  void kebabFromCamel() {
    assertThat(convert("fooBar", "kebab")).isEqualTo("foo-bar");
  }

  @Test
  void snakeFromCamel() {
    assertThat(convert("fooBar", "snake")).isEqualTo("foo_bar");
  }

  @Test
  void snakeFromSpaceSeparated() {
    assertThat(convert("foo bar", "snake")).isEqualTo("foo_bar");
  }

  @Test
  void titleFromSnake() {
    assertThat(convert("foo_bar_baz", "title")).isEqualTo("Foo Bar Baz");
  }

  @Test
  void alreadyTargetCaseIsStable() {
    assertThat(convert("fooBar", "camel")).isEqualTo("fooBar");
    assertThat(convert("foo-bar", "kebab")).isEqualTo("foo-bar");
    assertThat(convert("foo_bar", "snake")).isEqualTo("foo_bar");
  }

  @Test
  void acronymAtEndSplitsIntoSingleWord() {
    assertThat(convert("parseXML", "snake")).isEqualTo("parse_xml");
    assertThat(convert("parseXML", "kebab")).isEqualTo("parse-xml");
    assertThat(convert("parseXML", "camel")).isEqualTo("parseXML");
    assertThat(convert("parseXML", "title")).isEqualTo("Parse XML");
  }

  @Test
  void acronymInMiddleSplitsAroundCapitalizedWord() {
    assertThat(convert("XMLHttpRequest", "snake")).isEqualTo("xml_http_request");
    assertThat(convert("XMLHttpRequest", "kebab")).isEqualTo("xml-http-request");
  }

  @Test
  void digitsStayAttachedToWord() {
    assertThat(convert("v2Api", "snake")).isEqualTo("v2_api");
    assertThat(convert("v2Api", "kebab")).isEqualTo("v2-api");
  }

  @Test
  void modeIsCaseInsensitive() {
    assertThat(convert("fooBar", "SNAKE")).isEqualTo("foo_bar");
    assertThat(convert("fooBar", "Kebab")).isEqualTo("foo-bar");
  }

  @Test
  void nonAlphanumericCharactersActAsSeparators() {
    assertThat(convert("foo.bar/baz", "snake")).isEqualTo("foo_bar_baz");
    assertThat(convert("  foo  bar  ", "kebab")).isEqualTo("foo-bar");
  }

  @Test
  void emptyInputFails() {
    Result<CaseConvertOut> result = execute(new CaseConvertIn("", "snake"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("caseConvert.input is required");
  }

  @Test
  void nullInputFails() {
    Result<CaseConvertOut> result = execute(new CaseConvertIn(null, "snake"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessage("caseConvert.input is required");
  }

  @Test
  void nullModeFails() {
    Result<CaseConvertOut> result = execute(new CaseConvertIn("fooBar", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("caseConvert.mode must be");
  }

  @Test
  void unknownModeFails() {
    Result<CaseConvertOut> result = execute(new CaseConvertIn("fooBar", "pascal"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage(
            "caseConvert.mode must be 'camel', 'kebab', 'snake', or 'title', was: pascal");
  }

  private String convert(String input, String mode) {
    return execute(new CaseConvertIn(input, mode)).value().result();
  }

  private Result<CaseConvertOut> execute(CaseConvertIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
