package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.SecretIn;
import cbs.nova.starter.helper.model.SecretOut;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SecretHelperTest {

  private final SecretHelper helper = new SecretHelper();

  // --- bytes mode ---

  @Test
  void bytesDefaultsToHexEncoding() {
    String r = bytesMode(16, null).value().result();
    assertThat(r).hasSize(32).matches("[0-9a-f]{32}");
  }

  @Test
  void bytesHexDecodesToRequestedLength() {
    String r = bytesMode(24, "hex").value().result();
    assertThat(HexFormat.of().parseHex(r)).hasSize(24);
  }

  @Test
  void bytesBase64DecodesToRequestedLength() {
    String r = bytesMode(24, "base64").value().result();
    assertThat(Base64.getDecoder().decode(r)).hasSize(24);
  }

  @Test
  void bytesBase64UrlDecodesToRequestedLength() {
    String r = bytesMode(24, "base64url").value().result();
    assertThat(r).matches("[A-Za-z0-9_-]+={0,2}");
    assertThat(Base64.getUrlDecoder().decode(r)).hasSize(24);
  }

  @Test
  void bytesZeroLengthIsEmpty() {
    assertThat(bytesMode(0, "hex").value().result()).isEmpty();
  }

  @Test
  void bytesUnknownEncodingFails() {
    Result<SecretOut> result = execute(new SecretIn("bytes", 8, "rot13"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("encoding");
  }

  // --- token mode ---

  @Test
  void tokenMatchesBase64UrlAlphabet() {
    String r = tokenMode(43).value().result();
    assertThat(r).hasSize(43).matches("[A-Za-z0-9_-]{43}");
  }

  @Test
  void tokenZeroLengthIsEmpty() {
    assertThat(tokenMode(0).value().result()).isEmpty();
  }

  @Test
  void tokensAreUniqueAcrossCalls() {
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (int i = 0; i < 200; i++) {
      assertThat(seen.add(tokenMode(32).value().result())).isTrue();
    }
  }

  // --- validation ---

  @Test
  void bytesNullLengthFails() {
    Result<SecretOut> result = execute(new SecretIn("bytes", null, "hex"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("length");
  }

  @Test
  void bytesNegativeLengthFails() {
    Result<SecretOut> result = execute(new SecretIn("bytes", -1, "hex"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bytesLengthAboveLimitFails() {
    Result<SecretOut> result = execute(new SecretIn("bytes", 100001, "hex"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tokenNegativeLengthFails() {
    Result<SecretOut> result = execute(new SecretIn("token", -1, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tokenLengthAboveLimitFails() {
    Result<SecretOut> result = execute(new SecretIn("token", 100001, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknownModeFails() {
    Result<SecretOut> result = execute(new SecretIn("weird", 8, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("secret.mode");
  }

  // --- helpers ---

  private Result<SecretOut> execute(SecretIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }

  private Result<SecretOut> bytesMode(int length, String encoding) {
    return execute(new SecretIn("bytes", length, encoding));
  }

  private Result<SecretOut> tokenMode(int length) {
    return execute(new SecretIn("token", length, null));
  }
}
