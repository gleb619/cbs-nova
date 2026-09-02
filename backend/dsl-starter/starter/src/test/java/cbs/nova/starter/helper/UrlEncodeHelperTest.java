package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.UrlEncodeIn;
import cbs.nova.starter.helper.model.UrlEncodeOut;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UrlEncodeHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final UrlEncodeHelper helper = new UrlEncodeHelper();

  @Test
  void roundTripAscii() {
    String original = "Hello, World!";
    String encoded = encode(original, null, false);
    assertThat(URLDecoder.decode(encoded, StandardCharsets.UTF_8)).isEqualTo(original);
  }

  @Test
  void roundTripNonAsciiUtf8() {
    String original = "héllo—世界";
    String encoded = encode(original, null, false);
    assertThat(URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8))
            .isEqualTo(original);
  }

  @Test
  void spaceEncodesAsPercent20WhenFormFalse() {
    assertThat(encode("a b", null, false)).isEqualTo("a%20b");
  }

  @Test
  void spaceEncodesAsPlusWhenFormTrue() {
    assertThat(encode("a b", null, true)).isEqualTo("a+b");
  }

  @Test
  void literalPlusRoundTripsWhenFormFalse() {
    String original = "a+b";
    String encoded = encode(original, null, false);
    assertThat(encoded).isEqualTo("a%2Bb");
    assertThat(URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8))
            .isEqualTo(original);
  }

  @Test
  void blankInputIsEncoded() {
    assertThat(encode("   ", null, false)).isEqualTo("%20%20%20");
  }

  @Test
  void nullInputFails() {
    Result<UrlEncodeOut> result = execute(new UrlEncodeIn(null, null, false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("urlEncode.input is required");
  }

  @Test
  void emptyInputFails() {
    Result<UrlEncodeOut> result = execute(new UrlEncodeIn("", null, false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("urlEncode.input is required");
  }

  @Test
  void unknownCharsetFails() {
    Result<UrlEncodeOut> result = execute(new UrlEncodeIn("x", "NOPE-8", false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("urlEncode.charset is invalid: NOPE-8");
  }

  @Test
  void iso8859OneCharsetProducesExpectedSingleByteEncoding() {
    assertThat(encode("é", "ISO-8859-1", false)).isEqualTo("%E9");
  }

  private String encode(String input, String charset, boolean form) {
    return execute(new UrlEncodeIn(input, charset, form)).value().result();
  }

  private Result<UrlEncodeOut> execute(UrlEncodeIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
