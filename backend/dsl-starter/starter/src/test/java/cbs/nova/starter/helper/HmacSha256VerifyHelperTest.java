package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.HmacSha256VerifyIn;
import cbs.nova.starter.helper.model.HmacSha256VerifyOut;
import org.junit.jupiter.api.Test;

class HmacSha256VerifyHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final HmacSha256SignHelper signHelper = new HmacSha256SignHelper();
  private final HmacSha256VerifyHelper verifyHelper = new HmacSha256VerifyHelper();

  @Test
  void roundTripHex() {
    assertRoundTrip("The quick brown fox jumps over the lazy dog", "key", "hex");
  }

  @Test
  void roundTripBase64() {
    assertRoundTrip("The quick brown fox jumps over the lazy dog", "key", "base64");
  }

  @Test
  void roundTripBase64Url() {
    assertRoundTrip("The quick brown fox jumps over the lazy dog", "key", "base64url");
  }

  private void assertRoundTrip(String message, String secret, String encoding) {
    String signature = sign(message, secret, encoding);
    Result<HmacSha256VerifyOut> result = verify(
            new HmacSha256VerifyIn(message, secret, signature, encoding));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().valid()).isTrue();
  }

  @Test
  void crossEncodingReturnsFalse() {
    String signature = sign("message", "secret", "hex");
    Result<HmacSha256VerifyOut> result = verify(
            new HmacSha256VerifyIn("message", "secret", signature, "base64"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().valid()).isFalse();
  }

  @Test
  void tamperedMessageReturnsFalse() {
    String signature = sign("message", "secret", "hex");
    Result<HmacSha256VerifyOut> result = verify(
            new HmacSha256VerifyIn("tampered", "secret", signature, "hex"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().valid()).isFalse();
  }

  @Test
  void tamperedSignatureReturnsFalse() {
    String signature = sign("message", "secret", "hex");
    String tampered = signature.substring(0, signature.length() - 1) + "0";
    Result<HmacSha256VerifyOut> result = verify(
            new HmacSha256VerifyIn("message", "secret", tampered, "hex"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().valid()).isFalse();
  }

  @Test
  void wrongSecretReturnsFalse() {
    String signature = sign("message", "secret", "hex");
    Result<HmacSha256VerifyOut> result = verify(
            new HmacSha256VerifyIn("message", "wrong", signature, "hex"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().valid()).isFalse();
  }

  @Test
  void malformedSignatureReturnsFalseWithoutFailure() {
    Result<HmacSha256VerifyOut> result = verify(
            new HmacSha256VerifyIn("message", "secret", "!!!", "hex"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().valid()).isFalse();
  }

  @Test
  void emptySecretFails() {
    Result<HmacSha256VerifyOut> result = verify(new HmacSha256VerifyIn("x", "", "sig", "hex"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("hmacSha256Verify.secret must not be empty");
  }

  @Test
  void nullMessageFails() {
    Result<HmacSha256VerifyOut> result = verify(
            new HmacSha256VerifyIn(null, "secret", "sig", "hex"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("hmacSha256Verify.message is required");
  }

  @Test
  void blankSignatureFails() {
    Result<HmacSha256VerifyOut> result = verify(
            new HmacSha256VerifyIn("x", "secret", "   ", "hex"));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("hmacSha256Verify.signature is required");
  }

  // Verify uses MessageDigest.isEqual on decoded raw bytes to perform constant-time comparison,
  // avoiding timing attacks and normalizing differences such as hex case or Base64 padding.
  private String sign(String message, String secret, String encoding) {
    var signCtx = contextFactory.of(new cbs.nova.starter.helper.model.HmacSha256SignIn(
            message, secret, encoding), ExecutionMode.PREVIEW);
    return signHelper.execute(signCtx).value().signature();
  }

  private Result<HmacSha256VerifyOut> verify(HmacSha256VerifyIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return verifyHelper.execute(ctx);
  }
}
