package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.UrlDecodeIn;
import cbs.nova.starter.helper.model.UrlDecodeOut;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UrlDecodeHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final UrlDecodeHelper helper = new UrlDecodeHelper();

  @Test
  void roundTripAscii() {
    String original = "Hello, World!";
    String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8).replace("+", "%20");
    assertThat(decode(encoded, null, false)).isEqualTo(original);
  }

  @Test
  void roundTripNonAsciiUtf8() {
    String original = "héllo—世界";
    String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8).replace("+", "%20");
    assertThat(decode(encoded, null, false)).isEqualTo(original);
  }

  @Test
  void spaceDecodesFromPercent20WhenFormFalse() {
    assertThat(decode("a%20b", null, false)).isEqualTo("a b");
  }

  @Test
  void spaceDecodesFromPlusWhenFormTrue() {
    assertThat(decode("a+b", null, true)).isEqualTo("a b");
  }

  @Test
  void literalPlusRoundTripsWhenFormFalse() {
    assertThat(decode("a%2Bb", null, false)).isEqualTo("a+b");
  }

  @Test
  void invalidPercentSequenceFails() {
    Result<UrlDecodeOut> result = execute(new UrlDecodeIn("%ZZ", null, false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("urlDecode: invalid percent-encoded input");
  }

  @Test
  void nullInputFails() {
    Result<UrlDecodeOut> result = execute(new UrlDecodeIn(null, null, false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("urlDecode.input is required");
  }

  @Test
  void emptyInputFails() {
    Result<UrlDecodeOut> result = execute(new UrlDecodeIn("", null, false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("urlDecode.input is required");
  }

  @Test
  void unknownCharsetFails() {
    Result<UrlDecodeOut> result = execute(new UrlDecodeIn("x", "NOPE-8", false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("urlDecode.charset is invalid: NOPE-8");
  }

  @Test
  void iso8859OneCharsetProducesExpectedSingleByteDecoding() {
    assertThat(decode("%E9", "ISO-8859-1", false)).isEqualTo("é");
  }

  private String decode(String input, String charset, boolean form) {
    return execute(new UrlDecodeIn(input, charset, form)).value().result();
  }

  private Result<UrlDecodeOut> execute(UrlDecodeIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
