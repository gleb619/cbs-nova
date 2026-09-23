package cbs.nova.starter.helper;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.HtmlEscapeIn;
import cbs.nova.starter.helper.model.HtmlEscapeOut;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlEscapeHelperTest {

  private final HtmlEscapeHelper helper = new HtmlEscapeHelper();

  @Test
  void happyPathLeavesPlainTextUntouched() {
    assertThat(escape("Hello, World!")).isEqualTo("Hello, World!");
  }

  @Test
  void escapesAllSpecialCharacters() {
    assertThat(escape("<a href=\"x\">Tom & Jerry's</a>"))
            .isEqualTo("&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&#39;s&lt;/a&gt;");
  }

  @Test
  void emptyInputFails() {
    Result<HtmlEscapeOut> result = execute(new HtmlEscapeIn(""));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("htmlEscape.input is required");
  }

  @Test
  void nullInputFails() {
    Result<HtmlEscapeOut> result = execute(new HtmlEscapeIn(null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("htmlEscape.input is required");
  }

  @Test
  void nonAsciiPassthrough() {
    assertThat(escape("héllo—世界")).isEqualTo("héllo—世界");
  }

  private String escape(String input) {
    return execute(new HtmlEscapeIn(input)).value().result();
  }

  private Result<HtmlEscapeOut> execute(HtmlEscapeIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
