package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.Base64In;
import cbs.nova.starter.helper.model.Base64Out;
import org.junit.jupiter.api.Test;

class Base64HelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final Base64Helper helper = new Base64Helper();

  @Test
  void roundTripAscii() {
    String original = "Hello, World!";
    String encoded = encode(original, false);
    assertThat(decode(encoded, false)).isEqualTo(original);
  }

  @Test
  void roundTripNonAsciiUtf8() {
    String original = "héllo—世界";
    String encoded = encode(original, false);
    assertThat(decode(encoded, false)).isEqualTo(original);
  }

  @Test
  void urlSafeAvoidsStandardAlphabetChars() {
    // Input chosen so standard Base64 emits + and / characters.
    String encoded = encode("subjects?_d", true);
    assertThat(encoded).containsAnyOf("-", "_");
    assertThat(encoded).doesNotContain("+", "/");
  }

  @Test
  void oneByteInputProducesPadding() {
    assertThat(encode("A", false)).endsWith("==");
  }

  @Test
  void twoByteInputProducesPadding() {
    assertThat(encode("AB", false)).endsWith("=");
    assertThat(encode("AB", false)).doesNotEndWith("==");
  }

  @Test
  void decodeWithPadding() {
    assertThat(decode("QQ==", false)).isEqualTo("A");
    assertThat(decode("QUI=", false)).isEqualTo("AB");
  }

  @Test
  void urlSafeDecodeWithoutPadding() {
    assertThat(decode("QQ", true)).isEqualTo("A");
    assertThat(decode("QUI", true)).isEqualTo("AB");
  }

  @Test
  void encodeBlankInputIsEmpty() {
    assertThat(encode("", false)).isEqualTo("");
    assertThat(encode("   ", false)).isNotBlank();
  }

  @Test
  void decodeBlankInputIsEmpty() {
    assertThat(decode("", false)).isEqualTo("");
    assertThat(decode("   ", false)).isEqualTo("");
  }

  @Test
  void invalidDecodeInputFails() {
    Result<Base64Out> result = execute(new Base64In("!!!not base64!!!", "decode", false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("base64.decode: invalid base64 input");
  }

  @Test
  void unknownModeFails() {
    Result<Base64Out> result = execute(new Base64In("x", "frobnicate", false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("base64.mode must be 'encode' or 'decode', was: frobnicate");
  }

  @Test
  void nullInputFails() {
    Result<Base64Out> result = execute(new Base64In(null, "encode", false));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("base64.input is required");
  }

  private String encode(String input, boolean urlSafe) {
    return execute(new Base64In(input, "encode", urlSafe)).value().result();
  }

  private String decode(String input, boolean urlSafe) {
    return execute(new Base64In(input, "decode", urlSafe)).value().result();
  }

  private Result<Base64Out> execute(Base64In input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
