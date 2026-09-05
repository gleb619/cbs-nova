package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.CompressionIn;
import cbs.nova.starter.helper.model.CompressionOut;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CompressionHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final CompressionHelper helper = new CompressionHelper();

  @Test
  void gzipGunzipRoundTripAscii() {
    String original = "Hello, World!";
    String encoded = gzip(original, null);
    assertThat(gunzip(encoded)).isEqualTo(original);
  }

  @Test
  void gzipGunzipRoundTripUtf8Multibyte() {
    String original = "héllo wörld 日本語 — emoji-free but multi-byte";
    String encoded = gzip(original, null);
    assertThat(gunzip(encoded)).isEqualTo(original);
  }

  @Test
  void gzipEmptyInputFails() {
    Result<CompressionOut> result = execute(new CompressionIn("gzip", "", -1));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("compression.input is required");
  }

  @Test
  void gzipNullInputFails() {
    Result<CompressionOut> result = execute(new CompressionIn("gzip", null, -1));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("compression.input is required");
  }

  @Test
  void gzipLargeStringRoundTrips() {
    StringBuilder sb = new StringBuilder(100 * 1024);
    Random rng = new Random(42L);
    byte[] buf = new byte[100 * 1024];
    rng.nextBytes(buf);
    sb.append(new String(buf, StandardCharsets.ISO_8859_1));
    String original = sb.toString();
    String encoded = gzip(original, -1);
    assertThat(gunzip(encoded)).isEqualTo(original);
  }

  @Test
  void gunzipNotBase64Fails() {
    Result<CompressionOut> result = execute(new CompressionIn("gunzip", "!!!not base64!!!", -1));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("compression.gunzip: invalid base64 input");
  }

  @Test
  void gunzipCorruptedStreamFails() {
    byte[] garbage = new byte[256];
    new Random(7L).nextBytes(garbage);
    String bogus = Base64.getEncoder().encodeToString(garbage);
    Result<CompressionOut> result = execute(new CompressionIn("gunzip", bogus, -1));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("compression.gunzip:");
  }

  @Test
  void gunzipTruncatedGzipFails() {
    String encoded = gzip("this will be truncated", -1);
    byte[] raw = Base64.getDecoder().decode(encoded);
    byte[] truncated = new byte[Math.max(0, raw.length - 4)];
    System.arraycopy(raw, 0, truncated, 0, truncated.length);
    String truncatedB64 = Base64.getEncoder().encodeToString(truncated);
    Result<CompressionOut> result = execute(new CompressionIn("gunzip", truncatedB64, -1));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deflateInflateRoundTripAscii() {
    String original = "Hello, World! deflate style";
    String encoded = deflate(original, -1);
    assertThat(inflate(encoded)).isEqualTo(original);
  }

  @Test
  void inflateNotDeflateFails() {
    String notDeflate = Base64.getEncoder().encodeToString("not a deflate stream".getBytes());
    Result<CompressionOut> result = execute(new CompressionIn("inflate", notDeflate, -1));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("compression.inflate:");
  }

  @Test
  void levelNineNotLargerThanLevelOne() {
    String payload = "compress me compress me compress me compress me ".repeat(2000);
    String level1 = gzip(payload, 1);
    String level9 = gzip(payload, 9);
    assertThat(level9.length()).isLessThanOrEqualTo(level1.length());
  }

  @Test
  void levelNegativeOneAccepted() {
    String original = "default level please";
    String encoded = gzip(original, -1);
    assertThat(gunzip(encoded)).isEqualTo(original);
  }

  @Test
  void levelTenFails() {
    Result<CompressionOut> result = execute(new CompressionIn("gzip", "x", 10));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("compression.level must be between -1 and 9, was: 10");
  }

  @Test
  void levelNegativeTwoFails() {
    Result<CompressionOut> result = execute(new CompressionIn("deflate", "x", -2));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("compression.level must be between -1 and 9, was: -2");
  }

  @Test
  void unknownModeFails() {
    Result<CompressionOut> result = execute(new CompressionIn("brotli", "x", -1));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("compression.mode must be one of gzip, gunzip, deflate, inflate,"
                    + " was: brotli");
  }

  private String gzip(String input, Integer level) {
    return execute(new CompressionIn("gzip", input, level)).value().result();
  }

  private String gunzip(String input) {
    return execute(new CompressionIn("gunzip", input, -1)).value().result();
  }

  private String deflate(String input, Integer level) {
    return execute(new CompressionIn("deflate", input, level)).value().result();
  }

  private String inflate(String input) {
    return execute(new CompressionIn("inflate", input, -1)).value().result();
  }

  private Result<CompressionOut> execute(CompressionIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
