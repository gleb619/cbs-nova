package cbs.nova.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ContextEncryptionServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("shouldEncryptSensitiveFieldsWithENC Prefix")
  void shouldEncryptSensitiveFieldsWithENCPrefix() {
    ContextEncryptionService service = new ContextEncryptionService(objectMapper);
    String key = generateValidKey();
    ReflectionTestUtils.setField(service, "base64Key", key);

    Map<String, Object> context = Map.of("pan", "4111111111111111", "name", "John", "cvv", "123");

    String result = service.encrypt(context);

    assertTrue(result.contains("\"name\":\"John\""));
    // Encrypted fields should have ENC: prefix in the JSON value
    assertTrue(result.contains("\"pan\":\"ENC:"));
    assertTrue(result.contains("\"cvv\":\"ENC:"));
  }

  @Test
  @DisplayName("shouldLeaveNonSensitiveFieldsUnchanged")
  void shouldLeaveNonSensitiveFieldsUnchanged() {
    ContextEncryptionService service = new ContextEncryptionService(objectMapper);
    String key = generateValidKey();
    ReflectionTestUtils.setField(service, "base64Key", key);

    Map<String, Object> context = Map.of("pan", "4111111111111111", "name", "John", "amount", 5000);

    String result = service.encrypt(context);

    assertTrue(result.contains("\"name\":\"John\""));
    assertTrue(result.contains("\"amount\":5000"));
  }

  @Test
  @DisplayName("shouldDecryptEncryptedFieldsBackToPlaintext")
  void shouldDecryptEncryptedFieldsBackToPlaintext() {
    ContextEncryptionService service = new ContextEncryptionService(objectMapper);
    String key = generateValidKey();
    ReflectionTestUtils.setField(service, "base64Key", key);

    Map<String, Object> original = Map.of("pan", "4111111111111111", "name", "John", "cvv", "123");

    String encrypted = service.encrypt(original);
    Map<String, Object> decrypted = service.decrypt(encrypted);

    assertEquals("4111111111111111", decrypted.get("pan"));
    assertEquals("John", decrypted.get("name"));
    assertEquals("123", decrypted.get("cvv"));
  }

  @Test
  @DisplayName("shouldReturnMapAsIsWhenDecryptingNonEncryptedValues")
  void shouldReturnMapAsIsWhenDecryptingNonEncryptedValues() {
    ContextEncryptionService service = new ContextEncryptionService(objectMapper);
    // No key set — no-op mode
    ReflectionTestUtils.setField(service, "base64Key", "");

    Map<String, Object> context = Map.of("pan", "4111111111111111", "name", "John");

    String json = service.encrypt(context);
    Map<String, Object> result = service.decrypt(json);

    assertEquals("4111111111111111", result.get("pan"));
    assertEquals("John", result.get("name"));
  }

  @Test
  @DisplayName("shouldDoNothingWhenEncryptionKeyIsBlank")
  void shouldDoNothingWhenEncryptionKeyIsBlank() {
    ContextEncryptionService service = new ContextEncryptionService(objectMapper);
    ReflectionTestUtils.setField(service, "base64Key", "");

    Map<String, Object> context = Map.of("pan", "4111111111111111", "name", "John");

    String result = service.encrypt(context);

    // In no-op mode, sensitive fields remain as plaintext
    assertTrue(result.contains("\"pan\":\"4111111111111111\""));
    assertTrue(result.contains("\"name\":\"John\""));
  }

  @Test
  @DisplayName("shouldThrowIllegalStateExceptionWhenKeyIsMalformed")
  void shouldThrowIllegalStateExceptionWhenKeyIsMalformed() {
    ContextEncryptionService service = new ContextEncryptionService(objectMapper);
    ReflectionTestUtils.setField(service, "base64Key", "not-a-valid-base64-key!!!");

    Map<String, Object> context = Map.of("pan", "4111111111111111");

    IllegalStateException ex =
        Assertions.assertThrows(IllegalStateException.class, () -> service.encrypt(context));

    assertTrue(ex.getMessage().contains("Invalid encryption key"));
  }

  private String generateValidKey() {
    byte[] keyBytes = new byte[32];
    new SecureRandom().nextBytes(keyBytes);
    return Base64.getEncoder().encodeToString(keyBytes);
  }
}
