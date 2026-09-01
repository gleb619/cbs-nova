package cbs.nova.starter.helper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared HMAC-SHA256 logic for the sign and verify helpers.
 */
final class HmacSha256Support {

  private HmacSha256Support() {
  }

  static String normalizeEncoding(String encoding) {
    if (encoding == null || encoding.isBlank()) {
      return "hex";
    }
    return encoding.toLowerCase(Locale.ROOT);
  }

  static String[] encodingsList() {
    return new String[]{"hex", "base64", "base64url"};
  }

  static byte[] signToRawBytes(String message, String secret) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
  }

  static String encodeRawBytes(byte[] raw, String encoding) {
    return switch (encoding) {
      case "hex" -> HexFormat.of().formatHex(raw);
      case "base64" -> Base64.getEncoder().encodeToString(raw);
      case "base64url" -> Base64.getUrlEncoder().encodeToString(raw);
      default -> throw new IllegalArgumentException(
              "encoding must be one of: hex, base64, base64url (was: " + encoding + ")");
    };
  }

  static byte[] decodeSignature(String signature, String encoding) {
    return switch (encoding) {
      case "hex" -> HexFormat.of().parseHex(signature);
      case "base64" -> Base64.getDecoder().decode(signature);
      case "base64url" -> Base64.getUrlDecoder().decode(signature);
      default -> throw new IllegalArgumentException(
              "encoding must be one of: hex, base64, base64url (was: " + encoding + ")");
    };
  }

  static boolean constantTimeEquals(byte[] a, byte[] b) {
    return MessageDigest.isEqual(a, b);
  }
}
