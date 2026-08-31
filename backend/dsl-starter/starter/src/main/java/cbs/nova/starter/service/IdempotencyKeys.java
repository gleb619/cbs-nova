package cbs.nova.starter.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class IdempotencyKeys {

  private static final int DERIVED_ID_MAX_LENGTH = 32;
  private static final int MAX_KEY_LENGTH = 200;
  private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]+$");

  private IdempotencyKeys() {
  }

  /**
   * Derives a deterministic Temporal workflow id from a process name and an idempotency key.
   *
   * <p>
   * The derivation is:
   *
   * <pre>
   * sha256Hex(name + ":" + key)
   * </pre>
   *
   * truncated to the first 32 hex characters and prefixed with {@code "idem-"}. The same process
   * name and key always produce the same id, so duplicate submissions are mapped to the same
   * workflow execution.
   *
   * @param processName
   *          the DSL process name
   * @param key
   *          the validated idempotency key
   * @return the workflow/run id to use
   */
  public static String deriveRunId(String processName, String key) {
    byte[] input = (processName + ":" + key).getBytes(StandardCharsets.UTF_8);
    byte[] hash = sha256(input);
    String hex = HexFormat.of().formatHex(hash);
    return "idem-" + hex.substring(0, DERIVED_ID_MAX_LENGTH);
  }

  public static boolean isValid(String key) {
    if (key == null) {
      return false;
    }
    String trimmed = key.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_KEY_LENGTH) {
      return false;
    }
    return VALID_KEY_PATTERN.matcher(trimmed).matches();
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
