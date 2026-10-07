package cbs.nova.starter.service;

import cbs.nova.starter.core.StarterConstants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class IdempotencyKeys {

  private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]+$");

  private IdempotencyKeys() {
  }

  /**
   * Derives a deterministic Temporal workflow id from a process name, an idempotency key, and the
   * authenticated caller. The caller is included so that two different principals cannot collide on
   * the same short key for the same definition.
   *
   * <p>
   * The derivation is:
   *
   * <pre>
   * sha256Hex(name + ":" + key + ":" + caller)
   * </pre>
   *
   * truncated to the first 32 hex characters and prefixed with {@code "idem-"}. The same process
   * name, key, and caller always produce the same id, so duplicate submissions are mapped to the
   * same workflow execution.
   *
   * @param processName
   *          the DSL process name
   * @param key
   *          the validated idempotency key
   * @param caller
   *          the authenticated principal ({@code "anonymous"} when auth is disabled)
   * @return the workflow/run id to use
   */
  public static String deriveRunId(String processName, String key, String caller) {
    byte[] input = (processName + ":" + key + ":" + caller).getBytes(StandardCharsets.UTF_8);
    byte[] hash = sha256(input);
    String hex = HexFormat.of().formatHex(hash);
    return "idem-" + hex.substring(0, StarterConstants.IDEMPOTENCY_DERIVED_ID_MAX_LENGTH);
  }

  /**
   * Legacy two-argument derivation that defaults the caller to {@code "anonymous"}.
   */
  public static String deriveRunId(String processName, String key) {
    return deriveRunId(processName, key, "anonymous");
  }

  public static boolean isValid(String key) {
    if (key == null) {
      return false;
    }
    String trimmed = key.trim();
    if (trimmed.isEmpty() || trimmed.length() > StarterConstants.IDEMPOTENCY_MAX_KEY_LENGTH) {
      return false;
    }
    return VALID_KEY_PATTERN.matcher(trimmed).matches();
  }

  /**
   * SHA-256 fingerprint of the canonical JSON request body.
   */
  public static String fingerprint(tools.jackson.databind.ObjectMapper mapper, Object body) {
    try {
      Object value = body != null ? body : java.util.Map.of();
      byte[] json = mapper.writeValueAsBytes(value);
      return HexFormat.of().formatHex(sha256(json));
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalStateException("Failed to compute idempotency fingerprint", e);
    }
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
