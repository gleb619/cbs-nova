package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.JwtIn;
import cbs.nova.starter.helper.model.JwtOut;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.NonNull;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Encodes, decodes, signs, and verifies JSON Web Tokens (RFC 7519) using only the symmetric HMAC
 * family: {@code HS256}, {@code HS384}, and {@code HS512}.
 *
 * <h2>SECURITY WARNING — READ CAREFULLY</h2>
 *
 * <p>
 * The {@code parse} and {@code claim} modes are <b>DECODE ONLY</b>. They inspect the
 * base64url-decoded header and payload of a token without verifying any signature and without
 * checking the {@code exp} or {@code nbf} claims. They MUST NEVER be used to make trust or
 * authorization decisions. Any caller that uses the parsed values to decide "is this caller
 * authenticated?" or "is this request authorized?" has a critical security vulnerability.
 *
 * <p>
 * Only the {@code verify} mode establishes trust: it recomputes the HMAC over the header and
 * payload segments with the supplied secret and rejects the token on signature mismatch, expired
 * {@code exp}, or not-yet-valid {@code nbf}. Use {@code verify} for ALL authentication and
 * authorization flows.
 *
 * <h2>Modes</h2>
 *
 * <ul>
 * <li>{@code "parse"} — decode-only. Returns a map with {@code "header"}, {@code "payload"}, and
 * the raw base64url {@code "signature"} segment (the signature is NOT decoded or verified).</li>
 * <li>{@code "verify"} — cryptographically verify a token's signature (HS256/HS384/HS512) and
 * time-based claims. Only {@code "HS256"}, {@code "HS384"}, and {@code "HS512"} are accepted;
 * {@code "none"} and any other value are rejected unconditionally to defeat JWT "alg: none" and
 * alg-confusion attacks (CVE-2015-9235). The token's own header {@code alg} must match the
 * requested {@code algorithm} exactly.</li>
 * <li>{@code "sign"} — produce a compact JWS {@code "header.payload.signature"} string. The header
 * is fixed to {@code {"alg": <algorithm>, "typ": "JWT"}}. The payload is a copy of the caller's
 * claims with {@code iat} (now, Unix seconds) and {@code exp} (now + ttlSeconds, default 3600)
 * added (overwriting any pre-existing {@code iat}/{@code exp}).</li>
 * <li>{@code "claim"} — decode-only extraction of a single claim from the payload. Returns the
 * claim value without verifying the signature.</li>
 * </ul>
 *
 * <p>
 * The {@code mode} field is matched case-insensitively. The {@code algorithm} parameter is matched
 * case-insensitively but the token header {@code alg} must match the requested {@code algorithm}
 * exactly (case-sensitive) — this is part of the alg-confusion defense.
 */
@Helper(name = "jwt")
public class JwtHelper implements Executable<JwtIn, JwtOut> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String DEFAULT_ALG = "HS256";
  private static final long DEFAULT_TTL_SECONDS = 3600L;

  @Override
  public @NonNull Result<JwtOut> execute(@NonNull Context<JwtIn> ctx) {
    try {
      JwtIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "parse" -> Result.success(new JwtOut(parse(input.token())));
        case "verify" -> Result.success(new JwtOut(verify(input.token(), input.secret(),
                input.algorithm())));
        case "sign" -> Result.success(new JwtOut(sign(input.payload(), input.secret(),
                input.algorithm(), input.ttlSeconds())));
        case "claim" -> Result.success(new JwtOut(claim(input.token(), input.claimName())));
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "jwt.mode must be one of parse, verify, sign, claim, was: "
                                + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  // ---------------- parse ----------------

  private static @NonNull Map<String, Object> parse(String token) {
    String[] segments = splitToken(token);
    Map<String, Object> header = readJsonObject(segments[0], "header");
    Map<String, Object> payload = readJsonObject(segments[1], "payload");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("header", header);
    result.put("payload", payload);
    // Signature is the raw base64url segment — NEVER decoded or verified in this mode.
    result.put("signature", segments[2]);
    return result;
  }

  // ---------------- verify ----------------

  private static @NonNull Map<String, Object> verify(String token, String secret,
          String requestedAlgorithm) {
    if (secret == null || secret.isEmpty()) {
      throw new IllegalArgumentException("jwt.verify: secret is required");
    }
    String algorithm = (requestedAlgorithm == null || requestedAlgorithm.isBlank())
            ? DEFAULT_ALG
            : requestedAlgorithm;
    // Reject "none" and any unsupported algorithm BEFORE doing any cryptographic work.
    // CVE-2015-9235: "alg: none" attacks MUST be rejected unconditionally.
    String macName = macAlgorithmFor(algorithm);
    String[] segments = splitToken(token);
    Map<String, Object> header = readJsonObject(segments[0], "header");
    Map<String, Object> payload = readJsonObject(segments[1], "payload");

    // Alg-confusion defense: token's own header alg MUST match the requested algorithm exactly.
    // Reject "none" in the header even if the caller never requested it.
    Object headerAlgObj = header.get("alg");
    if (!(headerAlgObj instanceof String headerAlg)) {
      throw new IllegalArgumentException("jwt.verify: token header alg is missing or not a string");
    }
    if (headerAlg.equalsIgnoreCase("none")) {
      throw new IllegalArgumentException("jwt.verify: 'none' algorithm is not supported");
    }
    if (!headerAlg.equals(algorithm)) {
      throw new IllegalArgumentException("jwt.verify: signature mismatch");
    }

    String signingInput = segments[0] + "." + segments[1];
    byte[] expected = hmacRawBytes(macName, secret, signingInput);
    byte[] provided;
    try {
      provided = Base64.getUrlDecoder().decode(segments[2]);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("jwt.verify: signature mismatch", e);
    }
    // Constant-time comparison — never use String.equals or == on signature material.
    if (!MessageDigest.isEqual(expected, provided)) {
      throw new IllegalArgumentException("jwt.verify: signature mismatch");
    }

    // Time-based claim checks (RFC 7519 §4.1.4 exp and §4.1.5 nbf).
    long now = Instant.now().getEpochSecond();
    Object expObj = payload.get("exp");
    if (expObj != null) {
      Long exp = coerceLong(expObj);
      if (exp != null && now >= exp) {
        throw new IllegalArgumentException("jwt.verify: token expired");
      }
    }
    Object nbfObj = payload.get("nbf");
    if (nbfObj != null) {
      Long nbf = coerceLong(nbfObj);
      if (nbf != null && now < nbf) {
        throw new IllegalArgumentException("jwt.verify: token not yet valid");
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("header", header);
    result.put("payload", payload);
    return result;
  }

  // ---------------- sign ----------------

  private static @NonNull String sign(Map<String, Object> payload, String secret,
          String algorithm, Long ttlSeconds) {
    if (secret == null || secret.isEmpty()) {
      throw new IllegalArgumentException("jwt.sign: secret must not be empty");
    }
    if (payload == null) {
      throw new IllegalArgumentException("jwt.sign: payload is required");
    }
    String alg = (algorithm == null || algorithm.isBlank()) ? DEFAULT_ALG : algorithm;
    // Reject "none" and any unsupported algorithm at sign time too.
    String macName = macAlgorithmFor(alg);
    long ttl = (ttlSeconds == null) ? DEFAULT_TTL_SECONDS : ttlSeconds;
    if (ttl < 0) {
      throw new IllegalArgumentException("jwt.sign: ttlSeconds must not be negative");
    }

    long now = Instant.now().getEpochSecond();

    // Build a defensive copy of the payload and overwrite iat/exp with the values THIS call
    // computes (RFC 7519 standard claims). Caller's map is not mutated.
    ObjectNode headerNode = MAPPER.createObjectNode();
    headerNode.put("alg", alg);
    headerNode.put("typ", "JWT");

    ObjectNode payloadNode = MAPPER.createObjectNode();
    for (Map.Entry<String, Object> entry : payload.entrySet()) {
      payloadNode.set(entry.getKey(), toJsonNode(entry.getValue()));
    }
    payloadNode.put("iat", now);
    payloadNode.put("exp", now + ttl);

    String headerJson = writeCompact(headerNode);
    String payloadJson = writeCompact(payloadNode);
    String headerB64 = base64UrlNoPad(headerJson.getBytes(StandardCharsets.UTF_8));
    String payloadB64 = base64UrlNoPad(payloadJson.getBytes(StandardCharsets.UTF_8));
    String signingInput = headerB64 + "." + payloadB64;
    byte[] sigRaw = hmacRawBytes(macName, secret, signingInput);
    String signatureB64 = base64UrlNoPad(sigRaw);
    return signingInput + "." + signatureB64;
  }

  // ---------------- claim ----------------

  private static @NonNull Object claim(String token, String claimName) {
    if (claimName == null || claimName.isBlank()) {
      throw new IllegalArgumentException("jwt.claim: claimName is required");
    }
    String[] segments = splitToken(token);
    Map<String, Object> payload = readJsonObject(segments[1], "payload");
    if (!payload.containsKey(claimName)) {
      throw new IllegalArgumentException(
              "jwt.claim: claim '" + claimName + "' not present in token payload");
    }
    return payload.get(claimName);
  }

  // ---------------- shared helpers ----------------

  private static String[] splitToken(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("jwt: token is required");
    }
    String[] segments = token.split("\\.", -1);
    if (segments.length != 3) {
      throw new IllegalArgumentException(
              "jwt: malformed token, expected 3 base64url segments separated by '.', got "
                      + segments.length);
    }
    return segments;
  }

  private static @NonNull Map<String, Object> readJsonObject(String base64UrlSegment,
          String label) {
    byte[] bytes;
    try {
      bytes = Base64.getUrlDecoder().decode(base64UrlSegment);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("jwt: malformed base64url in " + label + " segment", e);
    }
    JsonNode node;
    try {
      node = MAPPER.readTree(bytes);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("jwt: malformed JSON in " + label + " segment", e);
    }
    if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
      throw new IllegalArgumentException(
              "jwt: " + label + " segment must be a JSON object");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) MAPPER.convertValue(node, Map.class);
    return map;
  }

  private static String macAlgorithmFor(String jwtAlgorithm) {
    if (jwtAlgorithm == null) {
      throw new IllegalArgumentException(
              "jwt: unsupported algorithm, must be one of HS256, HS384, HS512");
    }
    String upper = jwtAlgorithm.toUpperCase(Locale.ROOT);
    return switch (upper) {
      case "HS256" -> "HmacSHA256";
      case "HS384" -> "HmacSHA384";
      case "HS512" -> "HmacSHA512";
      default -> throw new IllegalArgumentException(
              "jwt: unsupported algorithm '" + jwtAlgorithm
                      + "', must be one of HS256, HS384, HS512");
    };
  }

  private static byte[] hmacRawBytes(String macName, String secret, String message) {
    try {
      Mac mac = Mac.getInstance(macName);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macName));
      return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("jwt: HMAC computation failed: " + e.getMessage(), e);
    }
  }

  private static @NonNull String base64UrlNoPad(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static @NonNull String writeCompact(@NonNull ObjectNode node) {
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
              "jwt: failed to serialize JSON: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * Best-effort conversion of an arbitrary Java value into a Jackson {@link JsonNode} so that the
   * caller's payload (a {@code Map<String, Object>}) can be round-tripped into JSON cleanly.
   */
  private static @NonNull JsonNode toJsonNode(@NonNull Object value) {
    if (value == null) {
      return MAPPER.nullNode();
    }
    if (value instanceof JsonNode existing) {
      return existing;
    }
    return MAPPER.valueToTree(value);
  }

  /**
   * Coerce a JSON-decoded numeric claim to a {@link Long}. JWT {@code exp} / {@code nbf} are
   * NumericDate values (RFC 7519 §2): fractional seconds are allowed but in practice seconds-since-
   * epoch is overwhelmingly common. We accept integer-valued or already-Long values.
   */
  private static Long coerceLong(Object value) {
    if (value instanceof Number n) {
      long asLong = n.longValue();
      if (n.doubleValue() == asLong) {
        return asLong;
      }
    }
    if (value instanceof String s) {
      try {
        return Long.parseLong(s.trim());
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    return null;
  }
}
