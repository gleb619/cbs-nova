package cbs.nova.starter.vhs.replay.fake;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.vhs.TapeEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SyntheticIdReplayFaker}.
 *
 * <p>
 * Covers the acceptance criteria in {@code docs/plans/T558-vhs-replay-faking.md}: synthetic
 * identifiers differ from the original, the per-session mapping table is consistent across calls,
 * domain types produce structurally valid fakes, the lookup table is honored with a sensible
 * fallback, and the disabled-faker is a no-op pass-through.
 */
class SyntheticIdReplayFakerTest {

  private static TapeEvent callStart(String callId, Object input) {
    return new TapeEvent(
            "1", 0, "call_start", "2026-01-01T00:00:00Z", 0L,
            new TapeEvent.CallMetadata(callId, "process", "Tgt", "op"),
            input, null,
            new TapeEvent.Timing(null, null, null), null, Map.of());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object o) {
    return (Map<String, Object>) o;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> asList(Object o) {
    return (List<Map<String, Object>>) o;
  }

  // ---------------------------------------------------------------------------------------------
  // Acceptance: replayed calls resolve to synthetic identifiers, not the original tape's real ids
  // ---------------------------------------------------------------------------------------------

  @Test
  void fakesReplaceIdsWithSyntheticValues() {
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.customerId", FakeRule.FakeType.id,
                    FakeRule.Generate.synthetic_uuid)),
            Map.of(), "seed");

    Map<String, Object> input = new HashMap<>(Map.of("customerId", "cust-9001-real"));
    TapeEvent out = faker.fake(callStart("c1", input));

    Object fake = asMap(out.input()).get("customerId");
    assertThat(fake).isInstanceOf(String.class);
    assertThat((String) fake).isNotEqualTo("cust-9001-real");
    assertThat((String) fake)
            .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  // ---------------------------------------------------------------------------------------------
  // Acceptance: two calls referencing the same real id receive the same synthetic replacement
  // ---------------------------------------------------------------------------------------------

  @Test
  void sameRealIdAcrossCallsGetsSameSynthetic() {
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true,
            List.of(
                    new FakeRule("$.customerId", FakeRule.FakeType.id,
                            FakeRule.Generate.synthetic_uuid),
                    new FakeRule("$.previousCustomerId", FakeRule.FakeType.id,
                            FakeRule.Generate.synthetic_uuid)),
            Map.of(), "seed");

    Map<String, Object> first = new HashMap<>(Map.of("customerId", "cust-42"));
    Map<String, Object> second = new HashMap<>(Map.of(
            "customerId", "cust-42",
            "previousCustomerId", "cust-42"));

    TapeEvent fakeFirst = faker.fake(callStart("c1", first));
    TapeEvent fakeSecond = faker.fake(callStart("c2", second));

    String firstId = (String) asMap(fakeFirst.input()).get("customerId");
    Map<String, Object> secondMap = asMap(fakeSecond.input());
    assertThat(secondMap.get("customerId")).isEqualTo(firstId);
    assertThat(secondMap.get("previousCustomerId")).isEqualTo(firstId);
  }

  // ---------------------------------------------------------------------------------------------
  // Acceptance: replay exercises the same branch — synthetic_hash preserves original length/shape
  // ---------------------------------------------------------------------------------------------

  @Test
  void syntheticHashPreservesShapeSoBranchesOnLengthStillFire() {
    String original = "REF-ABCDEFGHIJ-42";
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.ref", FakeRule.FakeType.reference,
                    FakeRule.Generate.synthetic_hash)),
            Map.of(), "seed");

    Map<String, Object> input = new HashMap<>(Map.of("ref", original));
    TapeEvent out = faker.fake(callStart("c1", input));

    String fake = (String) asMap(out.input()).get("ref");
    assertThat(fake).hasSize(original.length());
    // Alphanumeric only — downstream validation that expects only letters+digits passes.
    assertThat(fake).matches("[A-Za-z0-9]+");
    assertThat(fake).isNotEqualTo(original);
  }

  // ---------------------------------------------------------------------------------------------
  // Acceptance: iban/email/phone produce structurally valid fakes
  // ---------------------------------------------------------------------------------------------

  @Test
  void ibanFakeIsStructurallyValid() {
    String iban = SyntheticValueGenerator.iban();
    assertThat(iban).matches("[A-Z]{2}\\d{2}[0-9]+");
    // MOD-97 check digits round-trip: rearrange and verify remainder is 1.
    String rearranged = iban.substring(4) + iban.substring(0, 4);
    StringBuilder numeric = new StringBuilder();
    for (int i = 0; i < rearranged.length(); i++) {
      char c = rearranged.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        numeric.append((int) c - 55);
      } else {
        numeric.append(c);
      }
    }
    int rem = 0;
    for (int i = 0; i < numeric.length(); i++) {
      rem = (rem * 10 + (numeric.charAt(i) - '0')) % 97;
    }
    assertThat(rem).isEqualTo(1);
  }

  @Test
  void emailFakeMatchesRfcShape() {
    assertThat(SyntheticValueGenerator.email()).matches("[a-z0-9-]+@example\\.test");
    assertThat(SyntheticValueGenerator.email("Alice"))
            .matches("alice-[0-9a-f]{8}@example\\.test");
  }

  @Test
  void phoneFakeMatchesE164Shape() {
    assertThat(SyntheticValueGenerator.phone()).matches("\\+\\d{11}");
    assertThat(SyntheticValueGenerator.phone("44", 9)).matches("\\+44\\d{9}");
  }

  @Test
  void ibanRuleProducesStructurallyValidFake() {
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.iban", FakeRule.FakeType.iban,
                    FakeRule.Generate.synthetic_uuid)),
            Map.of(), "seed");

    Map<String, Object> input = new HashMap<>(Map.of("iban", "DE89370400440532013000"));
    String fake = (String) asMap(faker.fake(callStart("c1", input)).input()).get("iban");
    assertThat(fake).matches("[A-Z]{2}\\d{2}[0-9]+");
  }

  // ---------------------------------------------------------------------------------------------
  // Acceptance: pre-seeded lookup table honored; unmapped values fall back
  // ---------------------------------------------------------------------------------------------

  @Test
  void lookupTableIsHonoredWhenPresent() {
    Map<String, String> lookup = Map.of("cust-1", "FIXTURE-CUST-ONE");
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.customerId", FakeRule.FakeType.id,
                    FakeRule.Generate.lookup)),
            lookup, "seed");

    Map<String, Object> input = new HashMap<>(Map.of("customerId", "cust-1"));
    String fake = (String) asMap(faker.fake(callStart("c1", input)).input()).get("customerId");
    assertThat(fake).isEqualTo("FIXTURE-CUST-ONE");
  }

  @Test
  void lookupFallbackGeneratesSyntheticUuid() {
    Map<String, String> lookup = Map.of("cust-1", "FIXTURE-CUST-ONE");
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.customerId", FakeRule.FakeType.id,
                    FakeRule.Generate.lookup)),
            lookup, "seed");

    Map<String, Object> input = new HashMap<>(Map.of("customerId", "cust-unseen"));
    String fake = (String) asMap(faker.fake(callStart("c1", input)).input()).get("customerId");
    assertThat(fake).isNotEqualTo("cust-unseen");
    assertThat(fake).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  // ---------------------------------------------------------------------------------------------
  // Acceptance: wildcard fan-out works; disabled faker is a no-op
  // ---------------------------------------------------------------------------------------------

  @Test
  void wildcardFakesEveryElementOfAnArray() {
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(new HashMap<>(Map.of("iban", "DE0001")));
    accounts.add(new HashMap<>(Map.of("iban", "DE0002")));
    accounts.add(new HashMap<>(Map.of("iban", "DE0003")));

    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.accounts[*].iban", FakeRule.FakeType.id,
                    FakeRule.Generate.synthetic_uuid)),
            Map.of(), "seed");

    Map<String, Object> input = new HashMap<>();
    input.put("accounts", accounts);

    TapeEvent out = faker.fake(callStart("c1", input));
    List<Map<String, Object>> result = asList(asMap(out.input()).get("accounts"));

    assertThat(result).hasSize(3);
    for (Map<String, Object> entry : result) {
      assertThat(entry.get("iban")).isNotEqualTo("DE0001").isNotEqualTo("DE0002")
              .isNotEqualTo("DE0003");
    }
    // Per-session consistency: re-faking the same input yields identical output.
    TapeEvent again = faker.fake(callStart("c1", input));
    List<Map<String, Object>> againList = asList(asMap(again.input()).get("accounts"));
    for (int i = 0; i < result.size(); i++) {
      assertThat(againList.get(i).get("iban")).isEqualTo(result.get(i).get("iban"));
    }
  }

  @Test
  void disabledFakerReturnsInputEventUnchanged() {
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(false,
            List.of(new FakeRule("$.customerId", FakeRule.FakeType.id,
                    FakeRule.Generate.synthetic_uuid)),
            Map.of(), "seed");

    TapeEvent input = callStart("c1", Map.of("customerId", "cust-9"));
    TapeEvent out = faker.fake(input);

    assertThat(out).isSameAs(input);
    assertThat(faker.enabled()).isFalse();
  }

  @Test
  void enabledFlagWithoutRulesIsNoOp() {
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true, List.of(), Map.of(), "seed");
    assertThat(faker.enabled()).isFalse();

    TapeEvent input = callStart("c1", Map.of("customerId", "cust-9"));
    assertThat(faker.fake(input)).isSameAs(input);
  }

  @Test
  void missingPathLeavesEventUnchanged() {
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.does.not.exist", FakeRule.FakeType.id,
                    FakeRule.Generate.synthetic_uuid)),
            Map.of(), "seed");

    TapeEvent input = callStart("c1", Map.of("customerId", "cust-9"));
    assertThat(faker.fake(input)).isSameAs(input);
  }

  @Test
  void nullInputPassesThrough() {
    SyntheticIdReplayFaker faker = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.customerId", FakeRule.FakeType.id,
                    FakeRule.Generate.synthetic_uuid)),
            Map.of(), "seed");

    TapeEvent input = callStart("c1", null);
    assertThat(faker.fake(input)).isSameAs(input);
  }

  // ---------------------------------------------------------------------------------------------
  // Interface no-op + determinism
  // ---------------------------------------------------------------------------------------------

  @Test
  void noopFakerAlwaysReturnsInput() {
    VhsReplayFaker noop = VhsReplayFaker.noop();
    assertThat(noop.enabled()).isFalse();
    TapeEvent ev = callStart("c1", Map.of("x", 1));
    assertThat(noop.fake(ev)).isSameAs(ev);
  }

  @Test
  void syntheticValueIsDeterministicAcrossInstances() {
    // Two fakers with the same seed must produce the same synthetic for the same real value.
    SyntheticIdReplayFaker a = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.customerId", FakeRule.FakeType.id,
                    FakeRule.Generate.synthetic_uuid)),
            Map.of(), "seed");
    SyntheticIdReplayFaker b = new SyntheticIdReplayFaker(true,
            List.of(new FakeRule("$.customerId", FakeRule.FakeType.id,
                    FakeRule.Generate.synthetic_uuid)),
            Map.of(), "seed");

    String firstA = (String) asMap(a.fake(callStart("c1", Map.of("customerId", "abc"))).input())
            .get("customerId");
    String firstB = (String) asMap(b.fake(callStart("c1", Map.of("customerId", "abc"))).input())
            .get("customerId");
    assertThat(firstA).isEqualTo(firstB);
  }
}
