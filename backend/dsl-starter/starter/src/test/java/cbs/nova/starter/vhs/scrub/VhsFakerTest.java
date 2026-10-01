package cbs.nova.starter.vhs.scrub;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class VhsFakerTest {

  private static final String SEEN = "seed-1";
  private static final Pattern DIGITS_ONLY = Pattern.compile("\\d+");
  private static final Pattern ALPHANUMERIC_ONLY = Pattern.compile("[a-zA-Z0-9]+");
  private static final Pattern UUID_LIKE = Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private final VhsFaker faker = new VhsFaker();

  // ---- determinism ----------------------------------------------------------

  @Test
  void sameInputsProduceEqualOutput() {
    Object a = faker.fake("iban", "DE89370400440532013000", SEEN);
    Object b = faker.fake("iban", "DE89370400440532013000", SEEN);

    assertThat(a).isEqualTo(b);
  }

  @Test
  void differentFieldNameProducesDifferentOutput() {
    Object a = faker.fake("iban", "DE89370400440532013000", SEEN);
    Object b = faker.fake("accountId", "DE89370400440532013000", SEEN);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void differentOriginalProducesDifferentOutput() {
    Object a = faker.fake("iban", "DE89370400440532013000", SEEN);
    Object b = faker.fake("iban", "GB29NWBK60161331926819", SEEN);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void differentSeedProducesDifferentOutput() {
    Object a = faker.fake("iban", "DE89370400440532013000", "seed-1");
    Object b = faker.fake("iban", "DE89370400440532013000", "seed-2");

    assertThat(a).isNotEqualTo(b);
  }

  // ---- null / unknown -------------------------------------------------------

  @Test
  void nullOriginalMapsToNull() {
    assertThat(faker.fake("iban", null, SEEN)).isNull();
    assertThat(faker.fake("anything", null, null)).isNull();
  }

  @Test
  void unknownTypeIsReturnedAsIs() {
    Instant original = Instant.parse("2024-01-15T10:30:00Z");

    Object result = faker.fake("createdAt", original, SEEN);

    assertThat(result).isSameAs(original);
  }

  // ---- strings --------------------------------------------------------------

  @Test
  void allDigitStringKeepsShape() {
    String original = "12345678901234567890"; // 20 digits, no letters
    Object result = faker.fake("pin", original, SEEN);

    assertThat(result).isInstanceOf(String.class);
    String s = (String) result;
    assertThat(s).hasSize(original.length());
    assertThat(DIGITS_ONLY.matcher(s).matches()).isTrue();
  }

  @Test
  void nonDigitStringBecomesAlphanumericSameLength() {
    Object result = faker.fake("holder", "Alice", SEEN);

    assertThat(result).isInstanceOf(String.class);
    String s = (String) result;
    assertThat(s).hasSize("Alice".length());
    assertThat(ALPHANUMERIC_ONLY.matcher(s).matches()).isTrue();
  }

  @Test
  void mixedAlphaNumericStringTakesAlphanumericPath() {
    // IBAN-style "DE89..." starts with letters, so it takes the alphanumeric path, not digits.
    String original = "DE89370400440532013000";
    Object result = faker.fake("iban", original, SEEN);

    assertThat(result).isInstanceOf(String.class);
    String s = (String) result;
    assertThat(s).hasSize(original.length());
    assertThat(ALPHANUMERIC_ONLY.matcher(s).matches()).isTrue();
    // Output must not equal the original — it's a fake, not a passthrough.
    assertThat(s).isNotEqualTo(original);
  }

  @Test
  void emptyStringStillProducesLengthOne() {
    Object result = faker.fake("note", "", SEEN);

    assertThat(result).isInstanceOf(String.class);
    String s = (String) result;
    assertThat(s).hasSize(1);
    assertThat(ALPHANUMERIC_ONLY.matcher(s).matches()).isTrue();
  }

  // ---- secret-shaped field names -------------------------------------------

  @Test
  void secretLikeFieldNamesProduceUuidLikeToken() {
    String[] secretFields = {"token", "secret", "apiKey", "password", "authorization"};
    for (String field : secretFields) {
      Object result = faker.fake(field, "Bearer original-value", SEEN);

      assertThat(result)
              .as("field %s", field)
              .isInstanceOf(String.class);
      String s = (String) result;
      assertThat(UUID_LIKE.matcher(s).matches())
              .as("field %s produced %s", field, s)
              .isTrue();
      // Round-trip through UUID parsing to prove well-formedness.
      assertThat(UUID.fromString(s).toString()).isEqualTo(s);
    }
  }

  @Test
  void secretLikeFieldNameMatchingIsCaseInsensitive() {
    String[] mixedCase = {"Token", "SECRET", "ApiKey", "PaSsWoRd", "AUTHORIZATION"};
    for (String field : mixedCase) {
      Object result = faker.fake(field, "anything", SEEN);

      assertThat(result).isInstanceOf(String.class);
      assertThat(UUID_LIKE.matcher((String) result).matches())
              .as("field %s", field)
              .isTrue();
    }
  }

  @Test
  void nonSecretFieldNameDoesNotProduceUuidLikeToken() {
    Object result = faker.fake("iban", "DE89370400440532013000", SEEN);

    assertThat(result).isInstanceOf(String.class);
    assertThat(UUID_LIKE.matcher((String) result).matches()).isFalse();
  }

  // ---- scalar Java types ----------------------------------------------------

  @Test
  void integerStaysInteger() {
    Object result = faker.fake("count", 42, SEEN);

    assertThat(result).isInstanceOf(Integer.class);
  }

  @Test
  void longStaysLong() {
    Object result = faker.fake("epoch", 1_700_000_000_000L, SEEN);

    assertThat(result).isInstanceOf(Long.class);
  }

  @Test
  void doubleReturnsSameJavaTypeTypeOnly() {
    Object result = faker.fake("amount", 3.14d, SEEN);

    assertThat(result).isInstanceOf(Double.class);
    // Pin current behaviour: derived from raw long bits, so NaN/+Inf/-Inf are possible.
    // If a NaN/Infinity case reproduces here, stop and report (no production fix in this tier).
  }

  @Test
  void floatReturnsSameJavaTypeTypeOnly() {
    Object result = faker.fake("ratio", 0.5f, SEEN);

    assertThat(result).isInstanceOf(Float.class);
  }

  @Test
  void booleanStaysBoolean() {
    Object result = faker.fake("active", Boolean.TRUE, SEEN);

    assertThat(result).isInstanceOf(Boolean.class);
  }

  @Test
  void scalarDerivationIsDeterministicPerTriple() {
    Integer a = (Integer) faker.fake("count", 42, SEEN);
    Integer b = (Integer) faker.fake("count", 42, SEEN);
    Long la = (Long) faker.fake("epoch", 1_700_000_000_000L, SEEN);
    Long lb = (Long) faker.fake("epoch", 1_700_000_000_000L, SEEN);

    assertThat(a).isEqualTo(b);
    assertThat(la).isEqualTo(lb);
  }

  // ---- smoke reproducer for NaN / Infinity via raw bits ---------------------

  @Test
  void rawBitsDerivationDocumentsNaNInfinityPossibility() {
    // Sweep a few seeds; raw-bits derivation *can* yield NaN or +/-Infinity.
    // We do NOT assert the pathological case (depends on the digest), only the type contract.
    for (String seed : new String[]{"seed-1", "seed-2", "seed-3", "seed-4", "seed-5"}) {
      Object d = faker.fake("amount", 1.0d, seed);
      Object f = faker.fake("ratio", 1.0f, seed);

      assertThat(d).isInstanceOf(Double.class);
      assertThat(f).isInstanceOf(Float.class);
      // Sanity: not the original.
      assertThat(d).isNotEqualTo(1.0d);
      assertThat(f).isNotEqualTo(1.0f);
    }
  }
}
