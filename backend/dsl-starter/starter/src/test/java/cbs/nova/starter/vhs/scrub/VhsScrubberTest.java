package cbs.nova.starter.vhs.scrub;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VhsScrubberTest {

  private static final String MASK = "***REDACTED***";

  private static ScrubRule rule(String path, ScrubRule.Action action) {
    return new ScrubRule(path, action, null);
  }

  private static VhsScrubber enabled(ScrubRule... rules) {
    return new VhsScrubber(true, List.of(rules), MASK, "seed-1");
  }

  private static Map<String, Object> event() {
    List<Map<String, Object>> accounts = new ArrayList<>();
    accounts.add(new HashMap<>(Map.of("iban", "DE89370400440532013000", "holder", "Alice")));
    accounts.add(new HashMap<>(Map.of("iban", "GB29NWBK60161331926819", "holder", "Bob")));
    Map<String, Object> metadata = new HashMap<>(Map.of("authorization", "Bearer xyz"));
    Map<String, Object> event = new HashMap<>();
    event.put("cardNumber", "4111111111111111");
    event.put("token", "abc123secret");
    event.put("metadata", metadata);
    event.put("accounts", accounts);
    event.put("plain", "keep-me");
    return event;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> asList(Object value) {
    return (List<Map<String, Object>>) value;
  }

  @Test
  void remove_neverAppearsInScrubbedTree() {
    Map<String, Object> result = asMap(enabled(
            rule("$.cardNumber", ScrubRule.Action.remove),
            rule("$.metadata.authorization", ScrubRule.Action.remove)).scrub("input", event()));

    assertThat(result).doesNotContainKey("cardNumber");
    assertThat(asMap(result.get("metadata"))).doesNotContainKey("authorization");
    // Unconfigured field still present.
    assertThat(result).containsKey("plain");
  }

  @Test
  void mask_replacesWithConfiguredMaskValue() {
    Map<String, Object> original = event();
    Map<String, Object> result = asMap(
            enabled(rule("$.cardNumber", ScrubRule.Action.mask)).scrub("input", original));

    assertThat(result.get("cardNumber")).isEqualTo(MASK);
    // Structural cloning: the caller's tree is never mutated.
    assertThat(original).containsKey("cardNumber");
  }

  @Test
  void fake_isDeterministicAcrossTwoTapesWithSameSeed() {
    VhsScrubber a = new VhsScrubber(
            true, List.of(rule("$.accounts[*].iban", ScrubRule.Action.fake)), MASK, "seed-1");
    VhsScrubber b = new VhsScrubber(
            true, List.of(rule("$.accounts[*].iban", ScrubRule.Action.fake)), MASK, "seed-1");

    Map<String, Object> tape1 = asMap(a.scrub("input", event()));
    Map<String, Object> tape2 = asMap(b.scrub("input", event()));
    List<Map<String, Object>> accts1 = asList(tape1.get("accounts"));
    List<Map<String, Object>> accts2 = asList(tape2.get("accounts"));

    // Same original + same seed -> same fake.
    assertThat(accts1.get(0).get("iban")).isEqualTo(accts2.get(0).get("iban"));
    assertThat(accts1.get(1).get("iban")).isEqualTo(accts2.get(1).get("iban"));
    // The fake is not the original IBAN.
    assertThat(accts1.get(0).get("iban")).isNotEqualTo("DE89370400440532013000");
    // Wildcard fan-out leaves sibling fields untouched.
    assertThat(accts1.get(0).get("holder")).isEqualTo("Alice");
  }

  @Test
  void unconfiguredFieldsPassThroughByteForByte() {
    Map<String, Object> input = event();
    // Enabled scrubber, but the configured path does not exist -> skipped silently.
    Map<String, Object> result = asMap(
            enabled(rule("$.missingBranch.value", ScrubRule.Action.mask)).scrub("input", input));

    assertThat(result).isSameAs(input);
    assertThat(result.get("plain")).isEqualTo("keep-me");
    assertThat(asMap(result.get("metadata")).get("authorization")).isEqualTo("Bearer xyz");
  }

  @Test
  void missingPath_skipsSilentlyAndReturnsUnchangedReference() {
    Map<String, Object> input = event();
    VhsScrubber scrubber = enabled(rule("$.accounts[*].nope.missing", ScrubRule.Action.mask));

    // Calling twice exercises the bounded-WARN path; neither call fails nor mutates data.
    Object first = scrubber.scrub("input", input);
    Object second = scrubber.scrub("input", input);

    assertThat(first).isSameAs(input);
    assertThat(second).isSameAs(input);
  }

  @Test
  void disabled_noOp_returnsSameObjectReferences() {
    VhsScrubber scrubber = new VhsScrubber(
            false, List.of(rule("$.cardNumber", ScrubRule.Action.mask)), MASK, "seed");
    Map<String, Object> input = event();

    Object result = scrubber.scrub("input", input);

    assertThat(result).isSameAs(input);
    // Deep identity: no node was copied.
    Map<String, Object> resultMap = asMap(result);
    assertThat(resultMap.get("metadata")).isSameAs(input.get("metadata"));
    assertThat(asList(resultMap.get("accounts")).get(0))
            .isSameAs(asList(input.get("accounts")).get(0));
  }

  @Test
  void disabled_noExtraAllocation() {
    VhsScrubber scrubber = new VhsScrubber(
            false, List.of(rule("$.cardNumber", ScrubRule.Action.mask)), MASK, "seed");
    Map<String, Object> input = event();

    // Warm up the JIT so the measurement reflects steady-state no-op behaviour.
    for (int i = 0; i < 10_000; i++) {
      scrubber.scrub("input", input);
    }

    long threadId = Thread.currentThread().getId();
    long before = threadAllocatedBytes(threadId);
    scrubber.scrub("input", input);
    long after = threadAllocatedBytes(threadId);

    if (before >= 0L) {
      // No material allocation for the disabled pass-through. Tiny JIT/runtime noise is tolerated;
      // a real clone of even a small tree would allocate far more than this bound.
      assertThat(after - before).isLessThan(4096L);
    }
    // If thread-allocation tracking is unavailable the deep-identity test above still proves no
    // copy.
  }

  private static long threadAllocatedBytes(long threadId) {
    java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    if (bean instanceof com.sun.management.ThreadMXBean sun
            && sun.isThreadAllocatedMemorySupported()) {
      sun.setThreadAllocatedMemoryEnabled(true);
      return sun.getThreadAllocatedBytes(threadId);
    }
    return -1L;
  }
}
