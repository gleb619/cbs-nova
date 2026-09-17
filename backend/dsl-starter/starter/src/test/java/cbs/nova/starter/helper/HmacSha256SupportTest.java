package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class HmacSha256SupportTest {

  // RFC 4231-style known answer: HMAC-SHA256(key="key", msg="The quick brown fox jumps over the
  // lazy dog")
  private static final String QUICK_BROWN_FOX_KEY = "key";
  private static final String QUICK_BROWN_FOX_MSG = "The quick brown fox jumps over the lazy dog";
  private static final String QUICK_BROWN_FOX_HEX = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";

  // RFC 4231 Test Case 1: key = 0x0b * 20, data = "Hi There".
  private static final byte[] RFC4231_KEY_TC1 = new byte[]{
      0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
      0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
      0x0b, 0x0b, 0x0b, 0x0b
  };
  private static final String RFC4231_TC1_HEX = "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7";

  // ---------- encodingsList / normalizeEncoding ----------

  @Test
  void encodingsListReturnsHexBase64AndBase64Url() {
    assertThat(HmacSha256Support.encodingsList())
            .containsExactly("hex", "base64", "base64url");
  }

  @Test
  void normalizeEncodingNullReturnsHex() {
    assertThat(HmacSha256Support.normalizeEncoding(null)).isEqualTo("hex");
  }

  @Test
  void normalizeEncodingBlankReturnsHex() {
    assertThat(HmacSha256Support.normalizeEncoding("   ")).isEqualTo("hex");
  }

  @Test
  void normalizeEncodingEmptyReturnsHex() {
    assertThat(HmacSha256Support.normalizeEncoding("")).isEqualTo("hex");
  }

  @Test
  void normalizeEncodingLowercases() {
    assertThat(HmacSha256Support.normalizeEncoding("HEX")).isEqualTo("hex");
    assertThat(HmacSha256Support.normalizeEncoding("Base64URL")).isEqualTo("base64url");
  }

  @Test
  void normalizeEncodingPassesThroughUnknownValuesVerbatim() {
    // Normalisation is a pure lower-case step — unknown tokens flow through unchanged so the
    // encoder switch is the single source of truth for the "valid encoding" decision.
    assertThat(HmacSha256Support.normalizeEncoding("rot13")).isEqualTo("rot13");
  }

  // ---------- signToRawBytes ----------

  @Test
  void signToRawBytesMatchesQuickBrownFoxVector() throws Exception {
    byte[] raw = HmacSha256Support.signToRawBytes(QUICK_BROWN_FOX_MSG, QUICK_BROWN_FOX_KEY);
    assertThat(HexFormat.of().formatHex(raw)).isEqualTo(QUICK_BROWN_FOX_HEX);
  }

  @Test
  void signToRawBytesMatchesRfc4231TestCase1() throws Exception {
    String key = new String(RFC4231_KEY_TC1, java.nio.charset.StandardCharsets.UTF_8);
    byte[] raw = HmacSha256Support.signToRawBytes("Hi There", key);
    assertThat(HexFormat.of().formatHex(raw)).isEqualTo(RFC4231_TC1_HEX);
  }

  @Test
  void signToRawBytesAcceptsEmptyMessage() throws Exception {
    byte[] raw = HmacSha256Support.signToRawBytes("", "secret");
    assertThat(raw).hasSize(32);
  }

  @Test
  void signToRawBytesEmptySecretIsRejectedByJce() {
    // The JCE rejects an empty SecretKeySpec — confirm the failure mode so any future
    // pre-validation layer shows up as a behavioural diff rather than a silent regression.
    assertThatThrownBy(() -> HmacSha256Support.signToRawBytes("message", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Empty key");
  }

  @Test
  void signToRawBytesHandlesUtf8Message() throws Exception {
    // The implementation decodes via UTF-8 — a multibyte message must not throw or corrupt.
    byte[] raw = HmacSha256Support.signToRawBytes("héllo★world", "secret");
    assertThat(raw).hasSize(32);
  }

  @Test
  void signToRawBytesIsDeterministic() throws Exception {
    byte[] first = HmacSha256Support.signToRawBytes("msg", "key");
    byte[] second = HmacSha256Support.signToRawBytes("msg", "key");
    assertThat(first).isEqualTo(second);
  }

  // ---------- encodeRawBytes / decodeSignature ----------

  @Test
  void encodeRawBytesHexFormat() throws Exception {
    byte[] raw = HmacSha256Support.signToRawBytes(QUICK_BROWN_FOX_MSG, QUICK_BROWN_FOX_KEY);
    assertThat(HmacSha256Support.encodeRawBytes(raw, "hex")).isEqualTo(QUICK_BROWN_FOX_HEX);
  }

  @Test
  void encodeRawBytesBase64Format() throws Exception {
    byte[] raw = HmacSha256Support.signToRawBytes(QUICK_BROWN_FOX_MSG, QUICK_BROWN_FOX_KEY);
    String base64 = HmacSha256Support.encodeRawBytes(raw, "base64");
    assertThat(HexFormat.of().formatHex(Base64.getDecoder().decode(base64)))
            .isEqualTo(QUICK_BROWN_FOX_HEX);
  }

  @Test
  void encodeRawBytesBase64UrlFormat() throws Exception {
    byte[] raw = HmacSha256Support.signToRawBytes(QUICK_BROWN_FOX_MSG, QUICK_BROWN_FOX_KEY);
    String base64Url = HmacSha256Support.encodeRawBytes(raw, "base64url");
    assertThat(HexFormat.of().formatHex(Base64.getUrlDecoder().decode(base64Url)))
            .isEqualTo(QUICK_BROWN_FOX_HEX);
  }

  @Test
  void encodeRawBytesUnknownEncodingThrows() {
    byte[] raw = new byte[32];
    assertThatThrownBy(() -> HmacSha256Support.encodeRawBytes(raw, "rot13"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("encoding must be one of: hex, base64, base64url")
            .hasMessageContaining("was: rot13");
  }

  @Test
  void decodeSignatureHexRoundTrips() throws Exception {
    byte[] raw = HmacSha256Support.signToRawBytes(QUICK_BROWN_FOX_MSG, QUICK_BROWN_FOX_KEY);
    String hex = HmacSha256Support.encodeRawBytes(raw, "hex");
    assertThat(HmacSha256Support.decodeSignature(hex, "hex")).isEqualTo(raw);
  }

  @Test
  void decodeSignatureBase64RoundTrips() throws Exception {
    byte[] raw = HmacSha256Support.signToRawBytes(QUICK_BROWN_FOX_MSG, QUICK_BROWN_FOX_KEY);
    String base64 = HmacSha256Support.encodeRawBytes(raw, "base64");
    assertThat(HmacSha256Support.decodeSignature(base64, "base64")).isEqualTo(raw);
  }

  @Test
  void decodeSignatureBase64UrlRoundTrips() throws Exception {
    byte[] raw = HmacSha256Support.signToRawBytes(QUICK_BROWN_FOX_MSG, QUICK_BROWN_FOX_KEY);
    String base64Url = HmacSha256Support.encodeRawBytes(raw, "base64url");
    assertThat(HmacSha256Support.decodeSignature(base64Url, "base64url")).isEqualTo(raw);
  }

  @Test
  void decodeSignatureBadHexThrows() {
    assertThatThrownBy(() -> HmacSha256Support.decodeSignature("not-hex!", "hex"))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decodeSignatureUnknownEncodingThrows() {
    assertThatThrownBy(() -> HmacSha256Support.decodeSignature("abc", "rot13"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("encoding must be one of: hex, base64, base64url");
  }

  // ---------- constantTimeEquals ----------

  @Test
  void constantTimeEqualsReturnsTrueForEqualArrays() {
    byte[] a = {1, 2, 3, 4};
    byte[] b = {1, 2, 3, 4};
    assertThat(HmacSha256Support.constantTimeEquals(a, b)).isTrue();
  }

  @Test
  void constantTimeEqualsReturnsFalseForDifferentArrays() {
    byte[] a = {1, 2, 3, 4};
    byte[] b = {1, 2, 3, 5};
    assertThat(HmacSha256Support.constantTimeEquals(a, b)).isFalse();
  }

  @Test
  void constantTimeEqualsReturnsFalseForDifferentLengths() {
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 3, 4};
    assertThat(HmacSha256Support.constantTimeEquals(a, b)).isFalse();
  }

  @Test
  void constantTimeEqualsHandlesEmptyArrays() {
    assertThat(HmacSha256Support.constantTimeEquals(new byte[0], new byte[0])).isTrue();
    assertThat(HmacSha256Support.constantTimeEquals(new byte[0], new byte[]{1})).isFalse();
  }

  // ---------- cross-check: encode/decode/signing compose ----------

  @Test
  void encodeDecodeComposeOverRawSignature() throws Exception {
    byte[] raw = HmacSha256Support.signToRawBytes(QUICK_BROWN_FOX_MSG, QUICK_BROWN_FOX_KEY);
    for (String encoding : HmacSha256Support.encodingsList()) {
      String encoded = HmacSha256Support.encodeRawBytes(raw, encoding);
      assertThat(HmacSha256Support.decodeSignature(encoded, encoding))
              .as("round-trip via %s", encoding)
              .isEqualTo(raw);
    }
  }
}
