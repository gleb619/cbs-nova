package cbs.nova.starter.persistence;

import org.jspecify.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256/GCM application-level field encryptor.
 *
 * <p>
 * The configured key is hashed with SHA-256 to derive a 32-byte key. The encrypted value is stored
 * as Base64(iv + ciphertext + auth tag).
 */
public class AesFieldEncryptor implements FieldEncryptor {

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;

  private final SecretKey secretKey;
  private final SecureRandom secureRandom = new SecureRandom();

  public AesFieldEncryptor(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("AES encryption key must be configured");
    }
    try {
      byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(keyBytes);
      this.secretKey = new SecretKeySpec(hash, ALGORITHM);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize AES encryptor", e);
    }
  }

  @Override
  public @Nullable String encrypt(@Nullable String plain) {
    if (plain == null) {
      return null;
    }
    try {
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
      buffer.put(iv);
      buffer.put(cipherText);
      return Base64.getEncoder().encodeToString(buffer.array());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt field", e);
    }
  }

  @Override
  public @Nullable String decrypt(@Nullable String cipher) {
    if (cipher == null) {
      return null;
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(cipher);
      ByteBuffer buffer = ByteBuffer.wrap(decoded);
      byte[] iv = new byte[GCM_IV_LENGTH];
      buffer.get(iv);
      byte[] cipherText = new byte[buffer.remaining()];
      buffer.get(cipherText);
      Cipher c = Cipher.getInstance(TRANSFORMATION);
      c.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      return new String(c.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decrypt field", e);
    }
  }
}
