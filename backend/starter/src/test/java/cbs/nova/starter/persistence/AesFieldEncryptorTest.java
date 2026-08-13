package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

class AesFieldEncryptorTest {

  private static final String KEY_A = "test-key-1";
  private static final String KEY_B = "test-key-2";

  @Test
  void rejectsNullKey() {
    assertThatThrownBy(() -> new AesFieldEncryptor(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("AES encryption key must be configured");
  }

  @Test
  void rejectsBlankKey() {
    assertThatThrownBy(() -> new AesFieldEncryptor("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("AES encryption key must be configured");
  }

  @Test
  void constructsWithValidKey() {
    assertThat(new AesFieldEncryptor(KEY_A)).isNotNull();
  }

  @Test
  void encryptsNullToNull() {
    AesFieldEncryptor encryptor = new AesFieldEncryptor(KEY_A);
    assertThat(encryptor.encrypt(null)).isNull();
  }

  @Test
  void decryptsNullToNull() {
    AesFieldEncryptor encryptor = new AesFieldEncryptor(KEY_A);
    assertThat(encryptor.decrypt(null)).isNull();
  }

  @Test
  void roundTripsPlaintexts() {
    AesFieldEncryptor encryptor = new AesFieldEncryptor(KEY_A);
    for (String plain : new String[]{"hello world", "",
        "unic\u00f4de \ud83d\ude80 emoji \u00e9\u00e8\u00ea", "x".repeat(10_000)}) {
      String cipher = encryptor.encrypt(plain);
      assertThat(cipher).isNotNull();
      assertThat(encryptor.decrypt(cipher)).isEqualTo(plain);
    }
  }

  @Test
  void ciphertextIsBase64WithIvAndTagOverhead() {
    AesFieldEncryptor encryptor = new AesFieldEncryptor(KEY_A);
    String plain = "length-check";
    String cipher = encryptor.encrypt(plain);
    byte[] decoded = Base64.getDecoder().decode(cipher);
    int payloadBytes = plain.getBytes(StandardCharsets.UTF_8).length;
    assertThat(decoded.length).isGreaterThan(12 + payloadBytes);
  }

  @Test
  void encryptingTwiceYieldsDifferentCiphertexts() {
    AesFieldEncryptor encryptor = new AesFieldEncryptor(KEY_A);
    String plain = "fresh-iv";
    String first = encryptor.encrypt(plain);
    String second = encryptor.encrypt(plain);
    assertThat(first).isNotEqualTo(second);
    assertThat(encryptor.decrypt(first)).isEqualTo(plain);
    assertThat(encryptor.decrypt(second)).isEqualTo(plain);
  }

  @Test
  void tamperedCiphertextFailsAuthentication() {
    AesFieldEncryptor encryptor = new AesFieldEncryptor(KEY_A);
    byte[] decoded = Base64.getDecoder().decode(encryptor.encrypt("integrity"));
    decoded[12] ^= 0x01;
    String tampered = Base64.getEncoder().encodeToString(decoded);
    assertThatThrownBy(() -> encryptor.decrypt(tampered))
            .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void truncatedPayloadFails() {
    AesFieldEncryptor encryptor = new AesFieldEncryptor(KEY_A);
    String truncated = Base64.getEncoder().encodeToString(new byte[5]);
    assertThatThrownBy(() -> encryptor.decrypt(truncated))
            .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void decryptingWithDifferentKeyFails() {
    String cipher = new AesFieldEncryptor(KEY_A).encrypt("wrong-key");
    assertThatThrownBy(() -> new AesFieldEncryptor(KEY_B).decrypt(cipher))
            .isInstanceOf(IllegalStateException.class);
  }
}
