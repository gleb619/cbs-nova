package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.HexIn;
import cbs.nova.starter.helper.model.HexOut;
import org.junit.jupiter.api.Test;

class HexHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final HexHelper helper = new HexHelper();

  @Test
  void roundTripAscii() {
    String original = "Hello, World!";
    String encoded = encode(original);
    assertThat(decode(encoded)).isEqualTo(original);
  }

  @Test
  void roundTripNonAsciiUtf8() {
    String original = "héllo 🌍";
    String encoded = encode(original);
    assertThat(decode(encoded)).isEqualTo(original);
  }

  @Test
  void knownVectorsEncodeLowercase() {
    assertThat(encode("abc")).isEqualTo("616263");
    assertThat(encode("ABC")).isEqualTo("414243");
    assertThat(encode(" ")).isEqualTo("20");
    assertThat(encode("ÿ")).isEqualTo("c3bf");
  }

  @Test
  void knownVectorsDecode() {
    assertThat(decode("616263")).isEqualTo("abc");
    assertThat(decode("414243")).isEqualTo("ABC");
    assertThat(decode("20")).isEqualTo(" ");
    assertThat(decode("c3bf")).isEqualTo("ÿ");
  }

  @Test
  void oddLengthDecodeFails() {
    Result<HexOut> result = execute(new HexIn("abc", "decode"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("hex.decode: invalid hex input");
  }

  @Test
  void nonHexDecodeFails() {
    Result<HexOut> result = execute(new HexIn("zz", "decode"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("hex.decode: invalid hex input");
  }

  @Test
  void emptyEncodeFails() {
    Result<HexOut> result = execute(new HexIn("", "encode"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("hex.input is required");
  }

  @Test
  void emptyDecodeFails() {
    Result<HexOut> result = execute(new HexIn("", "decode"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("hex.input is required");
  }

  @Test
  void unknownModeFails() {
    Result<HexOut> result = execute(new HexIn("abc", "frobnicate"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("hex.mode must be 'encode' or 'decode', was: frobnicate");
  }

  @Test
  void nullInputFails() {
    Result<HexOut> result = execute(new HexIn(null, "encode"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("hex.input is required");
  }

  private String encode(String input) {
    return execute(new HexIn(input, "encode")).value().result();
  }

  private String decode(String input) {
    return execute(new HexIn(input, "decode")).value().result();
  }

  private Result<HexOut> execute(HexIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
