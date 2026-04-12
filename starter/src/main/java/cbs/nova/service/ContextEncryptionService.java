package cbs.nova.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ContextEncryptionService {

  static final Set<String> SENSITIVE_FIELD_NAMES =
      Set.of("pan", "cvv", "pin", "password", "secret");
  private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final String ENC_PREFIX = "ENC:";
  private final ObjectMapper objectMapper;

  @Value("${cbs.encryption.key:}")
  private String base64Key;

  private SecretKey secretKey;
  private boolean keyInitialized;

  public ContextEncryptionService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encrypt(Map<String, Object> context) {
    Map<String, Object> mutableContext = new HashMap<>(context);
    for (String fieldName : SENSITIVE_FIELD_NAMES) {
      if (mutableContext.containsKey(fieldName)
          && mutableContext.get(fieldName) != null
          && mutableContext.get(fieldName) instanceof String value) {
        mutableContext.put(fieldName, encryptValue(value));
      }
    }
    try {
      return objectMapper.writeValueAsString(mutableContext);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize context to JSON", e);
    }
  }

  public Map<String, Object> decrypt(String contextJson) {
    Map<String, Object> context;
    try {
      context = objectMapper.readValue(contextJson, new TypeReference<>() {
      });
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize context from JSON", e);
    }
    Map<String, Object> decrypted = new HashMap<>(context);
    for (String fieldName : SENSITIVE_FIELD_NAMES) {
      if (decrypted.containsKey(fieldName)
          && decrypted.get(fieldName) instanceof String value
          && value.startsWith(ENC_PREFIX)) {
        decrypted.put(fieldName, decryptValue(value));
      }
    }
    return decrypted;
  }

  private String encryptValue(String plaintext) {
    if (base64Key == null || base64Key.isBlank()) {
      return plaintext;
    }
    initKeyIfNeeded();
    try {
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      java.security.SecureRandom random = new java.security.SecureRandom();
      random.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

      return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt value", e);
    }
  }

  private String decryptValue(String encryptedValue) {
    if (base64Key == null || base64Key.isBlank()) {
      return encryptedValue;
    }
    initKeyIfNeeded();
    String base64Data = encryptedValue.substring(ENC_PREFIX.length());
    byte[] combined = Base64.getDecoder().decode(base64Data);

    byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
    byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH_BYTES];
    System.arraycopy(combined, 0, iv, 0, iv.length);
    System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

    try {
      Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
      byte[] decrypted = cipher.doFinal(ciphertext);
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decrypt value", e);
    }
  }

  private void initKeyIfNeeded() {
    if (keyInitialized) {
      return;
    }
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64Key);
      if (keyBytes.length != 32) {
        throw new IllegalStateException("Invalid encryption key: must be 32-byte Base64");
      }
      secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
      keyInitialized = true;
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Invalid encryption key: must be 32-byte Base64", e);
    }
  }
}
