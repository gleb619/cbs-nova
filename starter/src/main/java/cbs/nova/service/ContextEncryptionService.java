package cbs.nova.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class ContextEncryptionService {

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
    Map<String, Object> mutableContext = new HashMap<>();
    for (Map.Entry<String, Object> item : context.entrySet()) {
      Object value = item.getValue();
      if (value instanceof String s) {
        mutableContext.put(item.getKey(), encryptValue(s));
      } else {
        try {
          String jsonValue = objectMapper.writeValueAsString(value);
          mutableContext.put(item.getKey(), encryptValue(jsonValue));
        } catch (JacksonException e) {
          throw new IllegalStateException("Failed to serialize value to JSON", e);
        }
      }
    }
    try {
      return objectMapper.writeValueAsString(mutableContext);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize context to JSON", e);
    }
  }

  public Map<String, Object> decrypt(String contextJson) {
    Map<String, Object> context;
    try {
      context = objectMapper.readValue(contextJson, new TypeReference<>() {});
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to deserialize context from JSON", e);
    }
    Map<String, Object> decrypted = new HashMap<>();
    for (Map.Entry<String, Object> item : context.entrySet()) {
      Object value = item.getValue();
      if (value instanceof String s) {
        decrypted.put(item.getKey(), decryptValue(s));
      } else {
        String decryptedJson = decryptValue(value.toString());
        try {
          Object parsedValue = objectMapper.readValue(decryptedJson, Object.class);
          decrypted.put(item.getKey(), parsedValue);
        } catch (JacksonException e) {
          decrypted.put(item.getKey(), decryptedJson);
        }
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
      SecureRandom random = new SecureRandom();
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
      secretKey = new SecretKeySpec(keyBytes, "AES");
      keyInitialized = true;
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Invalid encryption key: must be 32-byte Base64", e);
    }
  }
}
