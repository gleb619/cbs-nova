package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.HmacSha256SignIn;
import cbs.nova.starter.helper.model.HmacSha256SignOut;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class HmacSha256SignHelperTest {

  private static final String QUICK_BROWN_FOX_VECTOR_HEX = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";

  private final ContextFactory contextFactory = new ContextFactory();
  private final HmacSha256SignHelper helper = new HmacSha256SignHelper();

  @Test
  void knownVectorHex() {
    Result<HmacSha256SignOut> result = execute(
            new HmacSha256SignIn("The quick brown fox jumps over the lazy dog", "key", "hex"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().signature()).isEqualTo(QUICK_BROWN_FOX_VECTOR_HEX);
    assertThat(result.value().encoding()).isEqualTo("hex");
  }

  @Test
  void knownVectorBase64() {
    Result<HmacSha256SignOut> result = execute(
            new HmacSha256SignIn("The quick brown fox jumps over the lazy dog", "key", "base64"));

    assertThat(result.isSuccess()).isTrue();
    byte[] raw = Base64.getDecoder().decode(result.value().signature());
    assertThat(HexFormat.of().formatHex(raw)).isEqualTo(QUICK_BROWN_FOX_VECTOR_HEX);
  }

  @Test
  void knownVectorBase64Url() {
    Result<HmacSha256SignOut> result = execute(
            new HmacSha256SignIn("The quick brown fox jumps over the lazy dog", "key",
                    "base64url"));

    assertThat(result.isSuccess()).isTrue();
    byte[] raw = Base64.getUrlDecoder().decode(result.value().signature());
    assertThat(HexFormat.of().formatHex(raw)).isEqualTo(QUICK_BROWN_FOX_VECTOR_HEX);
  }

  @Test
  void emptyMessageIsAllowed() {
    Result<HmacSha256SignOut> result = execute(new HmacSha256SignIn("", "secret", "hex"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().signature()).isNotBlank();
    assertThat(result.value().signature()).hasSize(64);
  }

  @Test
  void emptySecretFails() {
    Result<HmacSha256SignOut> result = execute(new HmacSha256SignIn("x", "", "hex"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("hmacSha256Sign.secret must not be empty");
  }

  @Test
  void nullMessageFails() {
    Result<HmacSha256SignOut> result = execute(new HmacSha256SignIn(null, "secret", "hex"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("hmacSha256Sign.message is required");
  }

  @Test
  void unknownEncodingFails() {
    Result<HmacSha256SignOut> result = execute(new HmacSha256SignIn("x", "secret", "rot13"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("encoding must be one of: hex, base64, base64url (was: rot13)");
  }

  private Result<HmacSha256SignOut> execute(HmacSha256SignIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
