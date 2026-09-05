package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.JwtIn;
import cbs.nova.starter.helper.model.JwtOut;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JwtHelperTest {

  private static final String SECRET = "super-secret-key";

  private final ContextFactory contextFactory = new ContextFactory();
  private final JwtHelper helper = new JwtHelper();

  // ---------- sign + verify round-trip ----------

  @Test
  void signThenVerifyRoundTripSucceeds() {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", "alice");
    claims.put("role", "admin");

    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS256", 3600L, claims, null))
            .value().result();

    Result<JwtOut> verify = execute(new JwtIn("verify", token, SECRET, "HS256", null, null, null));
    assertThat(verify.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> out = (Map<String, Object>) verify.value().result();
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) out.get("payload");
    assertThat(payload).containsEntry("sub", "alice");
    assertThat(payload).containsEntry("role", "admin");
    assertThat(payload).containsKeys("iat", "exp");
    Object exp = payload.get("exp");
    Object iat = payload.get("iat");
    assertThat(exp).isInstanceOfAny(Long.class, Integer.class);
    assertThat(iat).isInstanceOfAny(Long.class, Integer.class);
    assertThat(((Number) exp).longValue() - ((Number) iat).longValue()).isEqualTo(3600L);
  }

  @Test
  void signThenParseDecodeOnlySucceedsWithoutSecret() {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", "bob");
    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS256", 600L, claims, null))
            .value().result();

    Result<JwtOut> parseResult = execute(new JwtIn("parse", token, null, null, null, null, null));
    assertThat(parseResult.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> out = (Map<String, Object>) parseResult.value().result();
    @SuppressWarnings("unchecked")
    Map<String, Object> header = (Map<String, Object>) out.get("header");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) out.get("payload");
    assertThat(header).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
    assertThat(payload).containsEntry("sub", "bob");
    assertThat((String) out.get("signature")).isNotBlank();
    // Three '.'-separated segments
    assertThat(token.split("\\.", -1)).hasSize(3);
  }

  // ---------- verify failure modes ----------

  @Test
  void verifyWithWrongSecretFails() {
    Map<String, Object> claims = Map.of("sub", "alice");
    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS256", 3600L, claims, null))
            .value().result();
    Result<JwtOut> result = execute(new JwtIn("verify", token, "wrong-secret", "HS256", null, null,
            null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jwt.verify: signature mismatch");
  }

  @Test
  void verifyWithTamperedPayloadFails() {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", "alice");
    claims.put("role", "user");
    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS256", 3600L, claims, null))
            .value().result();

    String[] segments = token.split("\\.", -1);
    // Decode the payload, flip one byte in the role value, re-encode as base64url no-padding.
    byte[] payloadBytes = Base64.getUrlDecoder().decode(segments[1]);
    String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
    String tamperedJson = payloadJson.replace("\"role\":\"user\"", "\"role\":\"admin\"");
    String tamperedPayloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8));
    String tamperedToken = segments[0] + "." + tamperedPayloadB64 + "." + segments[2];

    Result<JwtOut> result = execute(
            new JwtIn("verify", tamperedToken, SECRET, "HS256", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jwt.verify: signature mismatch");
  }

  @Test
  void verifyRejectsTokenWithNoneAlgEvenIfSignatureStripped() {
    // Forge a token: header alg=none, empty signature, valid base64url-encoded payload.
    String headerJson = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
    String payloadJson = "{\"sub\":\"attacker\",\"exp\":9999999999}";
    String headerB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
    String payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    String token = headerB64 + "." + payloadB64 + ".";

    Result<JwtOut> result = execute(new JwtIn("verify", token, SECRET, "HS256", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("none");
  }

  @Test
  void verifyRejectsExplicitNoneAlgorithmRequest() {
    String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    String payloadJson = "{\"sub\":\"x\"}";
    String headerB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
    String payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    String token = headerB64 + "." + payloadB64 + ".c2ln";

    Result<JwtOut> result = execute(new JwtIn("verify", token, SECRET, "none", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verifyRejectsAlgConfusionHs256VsHs384() {
    Map<String, Object> claims = Map.of("sub", "alice");
    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS256", 3600L, claims, null))
            .value().result();

    // Token was signed with HS256, but caller asks verify to use HS384. The token's header alg
    // ("HS256") will not match the requested "HS384", so alg-confusion defense rejects.
    Result<JwtOut> result = execute(new JwtIn("verify", token, SECRET, "HS384", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jwt.verify: signature mismatch");

    // Also test the reverse: token signed HS384, verify asked for HS256.
    String token384 = (String) execute(
            new JwtIn("sign", null, SECRET, "HS384", 3600L, claims, null))
            .value().result();
    Result<JwtOut> result2 = execute(
            new JwtIn("verify", token384, SECRET, "HS256", null, null, null));
    assertThat(result2.isSuccess()).isFalse();
    assertThat(result2.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result2.cause()).hasMessage("jwt.verify: signature mismatch");
  }

  @Test
  void verifyWithExpiredTokenFails() {
    // Hand-craft a token with exp in the past. We sign it properly so signature passes.
    String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    String payloadJson = "{\"sub\":\"alice\",\"exp\":1}";
    String headerB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
    String payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    String signingInput = headerB64 + "." + payloadB64;
    String signatureB64 = hmacBase64Url("HmacSHA256", SECRET, signingInput);
    String token = signingInput + "." + signatureB64;

    Result<JwtOut> result = execute(new JwtIn("verify", token, SECRET, "HS256", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jwt.verify: token expired");
  }

  @Test
  void verifyWithNotYetValidTokenFails() {
    long futureNbf = java.time.Instant.now().getEpochSecond() + 3600;
    String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    String payloadJson = "{\"sub\":\"alice\",\"nbf\":" + futureNbf + "}";
    String headerB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
    String payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    String signingInput = headerB64 + "." + payloadB64;
    String signatureB64 = hmacBase64Url("HmacSHA256", SECRET, signingInput);
    String token = signingInput + "." + signatureB64;

    Result<JwtOut> result = execute(new JwtIn("verify", token, SECRET, "HS256", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jwt.verify: token not yet valid");
  }

  @Test
  void verifyWithWellFormedNonExpiredTokenSucceeds() {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", "alice");
    claims.put("scope", "read write");
    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS256", 3600L, claims, null))
            .value().result();

    Result<JwtOut> result = execute(new JwtIn("verify", token, SECRET, "HS256", null, null, null));
    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> out = (Map<String, Object>) result.value().result();
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) out.get("payload");
    assertThat(payload).containsEntry("sub", "alice").containsEntry("scope", "read write");
  }

  @Test
  void verifyRejectsTokenWithBase64SignatureGarbage() {
    String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    String payloadJson = "{\"sub\":\"alice\"}";
    String headerB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
    String payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    String token = headerB64 + "." + payloadB64 + ".!!!not-base64!!!";

    Result<JwtOut> result = execute(new JwtIn("verify", token, SECRET, "HS256", null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jwt.verify: signature mismatch");
  }

  // ---------- sign failure modes ----------

  @Test
  void signWithNegativeTtlSecondsFails() {
    Map<String, Object> claims = Map.of("sub", "alice");
    Result<JwtOut> result = execute(new JwtIn("sign", null, SECRET, "HS256", -1L, claims, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jwt.sign: ttlSeconds must not be negative");
  }

  @Test
  void signWithUnsupportedAlgorithmFails() {
    Map<String, Object> claims = Map.of("sub", "alice");
    Result<JwtOut> result = execute(new JwtIn("sign", null, SECRET, "HS128", 3600L, claims, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("unsupported algorithm");
  }

  @Test
  void signWithNoneAlgorithmFails() {
    Map<String, Object> claims = Map.of("sub", "alice");
    Result<JwtOut> result = execute(new JwtIn("sign", null, SECRET, "none", 3600L, claims, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("unsupported algorithm");
  }

  @Test
  void signWithEmptySecretFails() {
    Map<String, Object> claims = Map.of("sub", "alice");
    Result<JwtOut> result = execute(new JwtIn("sign", null, "", "HS256", 3600L, claims, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jwt.sign: secret must not be empty");
  }

  @Test
  void signWithNullPayloadFails() {
    Result<JwtOut> result = execute(new JwtIn("sign", null, SECRET, "HS256", 3600L, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("jwt.sign: payload is required");
  }

  @Test
  void signAndVerifyHs384RoundTrip() {
    Map<String, Object> claims = Map.of("sub", "alice");
    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS384", 3600L, claims, null))
            .value().result();
    Result<JwtOut> verify = execute(new JwtIn("verify", token, SECRET, "HS384", null, null, null));
    assertThat(verify.isSuccess()).isTrue();
  }

  @Test
  void signAndVerifyHs512RoundTrip() {
    Map<String, Object> claims = Map.of("sub", "alice");
    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS512", 3600L, claims, null))
            .value().result();
    Result<JwtOut> verify = execute(new JwtIn("verify", token, SECRET, "HS512", null, null, null));
    assertThat(verify.isSuccess()).isTrue();
  }

  // ---------- claim mode (decode-only) ----------

  @Test
  void claimExtractsValueWithoutSecret() {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", "alice");
    claims.put("role", "admin");
    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS256", 3600L, claims, null))
            .value().result();

    Result<JwtOut> subResult = execute(new JwtIn("claim", token, null, null, null, null, "sub"));
    assertThat(subResult.isSuccess()).isTrue();
    assertThat(subResult.value().result()).isEqualTo("alice");

    Result<JwtOut> roleResult = execute(new JwtIn("claim", token, null, null, null, null, "role"));
    assertThat(roleResult.isSuccess()).isTrue();
    assertThat(roleResult.value().result()).isEqualTo("admin");
  }

  @Test
  void claimMissingClaimNameFails() {
    Map<String, Object> claims = Map.of("sub", "alice");
    String token = (String) execute(new JwtIn("sign", null, SECRET, "HS256", 3600L, claims, null))
            .value().result();

    Result<JwtOut> result = execute(new JwtIn("claim", token, null, null, null, null, "missing"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("missing");
  }

  @Test
  void claimOnMalformedTokenFails() {
    Result<JwtOut> result = execute(
            new JwtIn("claim", "not.a.valid.token", null, null, null, null, "sub"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // ---------- parse failure modes ----------

  @Test
  void parseMalformedTokenFails() {
    Result<JwtOut> result = execute(new JwtIn("parse", "only-one-segment", null, null, null, null,
            null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("malformed token");

    Result<JwtOut> result2 = execute(new JwtIn("parse", "a.b.c.d", null, null, null, null, null));
    assertThat(result2.isSuccess()).isFalse();
    assertThat(result2.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseMalformedBase64InSegmentFails() {
    Result<JwtOut> result = execute(new JwtIn("parse", "!!!.eyJzdWIiOiJ4In0.sig", null, null, null,
            null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("malformed base64url");
  }

  @Test
  void parseMalformedJsonInSegmentFails() {
    String badJsonB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("not-json".getBytes(StandardCharsets.UTF_8));
    Result<JwtOut> result = execute(
            new JwtIn("parse", badJsonB64 + ".eyJzdWIiOiJ4In0.sig", null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("malformed JSON");
  }

  // ---------- mode dispatch ----------

  @Test
  void unknownModeFails() {
    Result<JwtOut> result = execute(
            new JwtIn("frobnicate", null, SECRET, "HS256", 3600L, Map.of("sub", "x"), null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("jwt.mode must be one of parse, verify, sign, claim, was: frobnicate");
  }

  @Test
  void nullModeFails() {
    Result<JwtOut> result = execute(new JwtIn(null, null, SECRET, "HS256", 3600L,
            Map.of("sub", "x"), null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage("jwt.mode must be one of parse, verify, sign, claim, was: null");
  }

  @Test
  void modeIsCaseInsensitive() {
    Map<String, Object> claims = Map.of("sub", "alice");
    String token = (String) execute(new JwtIn("SIGN", null, SECRET, "HS256", 3600L, claims, null))
            .value().result();
    Result<JwtOut> verify = execute(new JwtIn("VERIFY", token, SECRET, "HS256", null, null, null));
    assertThat(verify.isSuccess()).isTrue();
  }

  // ---------- helpers ----------

  private static String hmacBase64Url(String macName, String secret, String message) {
    try {
      Mac mac = Mac.getInstance(macName);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macName));
      byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Result<JwtOut> execute(JwtIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
