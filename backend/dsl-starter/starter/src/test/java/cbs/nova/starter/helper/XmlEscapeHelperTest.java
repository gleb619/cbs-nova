package cbs.nova.starter.helper;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.XmlEscapeIn;
import cbs.nova.starter.helper.model.XmlEscapeOut;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class XmlEscapeHelperTest {

  private final XmlEscapeHelper helper = new XmlEscapeHelper();

  @Test
  void happyPathLeavesPlainTextUntouched() {
    assertThat(escape("Hello, World!")).isEqualTo("Hello, World!");
  }

  @Test
  void escapesAllSpecialCharacters() {
    assertThat(escape("<a href=\"x\">Tom & Jerry's</a>"))
            .isEqualTo("&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&apos;s&lt;/a&gt;");
  }

  @Test
  void emptyInputFails() {
    Result<XmlEscapeOut> result = execute(new XmlEscapeIn(""));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("xmlEscape.input is required");
  }

  @Test
  void nullInputFails() {
    Result<XmlEscapeOut> result = execute(new XmlEscapeIn(null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("xmlEscape.input is required");
  }

  @Test
  void nonAsciiPassthrough() {
    assertThat(escape("héllo—世界")).isEqualTo("héllo—世界");
  }

  private String escape(String input) {
    return execute(new XmlEscapeIn(input)).value().result();
  }

  private Result<XmlEscapeOut> execute(XmlEscapeIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
