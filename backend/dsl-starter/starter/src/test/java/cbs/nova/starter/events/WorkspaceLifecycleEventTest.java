package cbs.nova.starter.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.events.WorkspaceLifecycleEvent.DraftsCommitted;
import cbs.nova.starter.events.WorkspaceLifecycleEvent.DraftsRepoFetched;
import cbs.nova.starter.events.WorkspaceLifecycleEvent.DraftsRepoPulled;
import cbs.nova.starter.events.WorkspaceLifecycleEvent.DraftsRepoRebased;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * T781: pins the public contract of {@link WorkspaceLifecycleEvent} — accessor values for each
 * record, compact-ctor guards, Jackson polymorphic round-trip via the sealed-interface type, and
 * the current (failure) behaviour for unknown {@code eventType} discriminators.
 */
class WorkspaceLifecycleEventTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  // ---------- accessors ----------

  @Nested
  class Accessors {

    @Test
    void draftsRepoFetchedExposesBranchAndWorkspaceAggregate() {
      DraftsRepoFetched event = new DraftsRepoFetched(
              "feat/T781", true, 2, 3, Instant.parse("2026-09-10T12:00:00Z"), "corr-1");

      assertThat(event.eventType()).isEqualTo("DraftsRepoFetched");
      assertThat(event.aggregateType()).isEqualTo("workspace");
      assertThat(event.aggregateId()).isEqualTo("feat/T781");
      assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    void draftsRepoPulledExposesBranchAndWorkspaceAggregate() {
      DraftsRepoPulled event = new DraftsRepoPulled(
              "feat/T781",
              "rebase",
              true,
              1,
              0,
              Instant.parse("2026-09-10T12:00:00Z"),
              "corr-2");

      assertThat(event.eventType()).isEqualTo("DraftsRepoPulled");
      assertThat(event.aggregateType()).isEqualTo("workspace");
      assertThat(event.aggregateId()).isEqualTo("feat/T781");
      assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    void draftsRepoRebasedExposesBranchAndWorkspaceAggregate() {
      DraftsRepoRebased event = new DraftsRepoRebased(
              "feat/T781", true, 0, 0, Instant.parse("2026-09-10T12:00:00Z"), "corr-3");

      assertThat(event.eventType()).isEqualTo("DraftsRepoRebased");
      assertThat(event.aggregateType()).isEqualTo("workspace");
      assertThat(event.aggregateId()).isEqualTo("feat/T781");
      assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    void draftsCommittedExposesCommitIdAndWorkspaceAggregate() {
      DraftsCommitted event = new DraftsCommitted(
              "abc123",
              2,
              "batch commit",
              List.of("a.dsl", "b.dsl"),
              Instant.parse("2026-09-10T12:00:00Z"),
              "corr-4");

      assertThat(event.eventType()).isEqualTo("DraftsCommitted");
      assertThat(event.aggregateType()).isEqualTo("workspace");
      assertThat(event.aggregateId()).isEqualTo("abc123");
      assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    void nullableFieldsMayBeNull() {
      DraftsCommitted event = new DraftsCommitted("abc123", 1, "msg", null, null, null);

      assertThat(event.paths()).isNull();
      assertThat(event.occurredAt()).isNull();
      assertThat(event.correlationId()).isNull();
    }
  }

  // ---------- compact-ctor guards ----------

  @Nested
  class CompactCtorGuards {

    @Test
    void blankBranchRejectedOnFetched() {
      assertThatThrownBy(() -> new DraftsRepoFetched(
              "", true, 0, 0, null, null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("branch");
    }

    @Test
    void blankBranchRejectedOnPulled() {
      assertThatThrownBy(() -> new DraftsRepoPulled(
              "   ", "rebase", true, 0, 0, null, null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("branch");
    }

    @Test
    void blankBranchRejectedOnRebased() {
      assertThatThrownBy(() -> new DraftsRepoRebased(
              "", true, 0, 0, null, null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("branch");
    }

    @Test
    void blankCommitIdRejectedOnCommitted() {
      assertThatThrownBy(() -> new DraftsCommitted(
              "", 1, "msg", null, null, null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("commitId");
    }

    @Test
    void blankMessageRejectedOnCommitted() {
      assertThatThrownBy(() -> new DraftsCommitted(
              "abc123", 1, "   ", null, null, null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("message");
    }
  }

  // ---------- Jackson round-trip ----------

  @Nested
  class JacksonRoundTrip {

    @Test
    void serializeAndDeserializeDraftsRepoFetched() throws Exception {
      DraftsRepoFetched original = new DraftsRepoFetched(
              "feat/T781", true, 2, 3, Instant.parse("2026-09-10T12:00:00Z"), "corr-1");

      String json = mapper.writeValueAsString(original);

      ObjectNode tree = (ObjectNode) mapper.readTree(json);
      assertThat(tree.get("eventType").asText()).isEqualTo("DraftsRepoFetched");
      assertThat(tree.get("aggregateType").asText()).isEqualTo("workspace");
      assertThat(tree.get("aggregateId").asText()).isEqualTo("feat/T781");

      WorkspaceLifecycleEvent roundTripped = (WorkspaceLifecycleEvent) mapper.readValue(json,
              WorkspaceLifecycleEvent.class);
      assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void serializeAndDeserializeDraftsRepoPulled() throws Exception {
      DraftsRepoPulled original = new DraftsRepoPulled(
              "feat/T781", "rebase", true, 1, 0, null, "corr-2");

      String json = mapper.writeValueAsString(original);

      WorkspaceLifecycleEvent roundTripped = (WorkspaceLifecycleEvent) mapper.readValue(json,
              WorkspaceLifecycleEvent.class);
      assertThat(roundTripped).isEqualTo(original);
      assertThat(roundTripped.eventType()).isEqualTo("DraftsRepoPulled");
    }

    @Test
    void serializeAndDeserializeDraftsRepoRebased() throws Exception {
      DraftsRepoRebased original = new DraftsRepoRebased(
              "feat/T781", true, 0, 0, null, null);

      String json = mapper.writeValueAsString(original);

      WorkspaceLifecycleEvent roundTripped = (WorkspaceLifecycleEvent) mapper.readValue(json,
              WorkspaceLifecycleEvent.class);
      assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void serializeAndDeserializeDraftsCommittedWithNullables() throws Exception {
      DraftsCommitted original = new DraftsCommitted(
              "abc123", 2, "batch commit", null, null, null);

      String json = mapper.writeValueAsString(original);

      ObjectNode tree = (ObjectNode) mapper.readTree(json);
      assertThat(tree.get("eventType").asText()).isEqualTo("DraftsCommitted");
      // Pin current behaviour: Jackson 3 default serialisation emits @Nullable fields as
      // JSON null (not absent). Consumers must tolerate the keys with null values.
      assertThat(tree.get("paths").isNull()).isTrue();
      assertThat(tree.has("paths")).isTrue();
      assertThat(tree.get("occurredAt").isNull()).isTrue();
      assertThat(tree.has("occurredAt")).isTrue();
      assertThat(tree.get("correlationId").isNull()).isTrue();
      assertThat(tree.has("correlationId")).isTrue();

      WorkspaceLifecycleEvent roundTripped = (WorkspaceLifecycleEvent) mapper.readValue(json,
              WorkspaceLifecycleEvent.class);
      assertThat(roundTripped).isEqualTo(original);
      assertThat(((DraftsCommitted) roundTripped).paths()).isNull();
      assertThat(roundTripped.occurredAt()).isNull();
      assertThat(roundTripped.correlationId()).isNull();
    }

    @Test
    void serializeAndDeserializeDraftsCommittedWithPaths() throws Exception {
      DraftsCommitted original = new DraftsCommitted(
              "abc123",
              2,
              "batch commit",
              List.of("a.dsl", "b.dsl"),
              Instant.parse("2026-09-10T12:00:00Z"),
              "corr-4");

      String json = mapper.writeValueAsString(original);

      WorkspaceLifecycleEvent roundTripped = (WorkspaceLifecycleEvent) mapper.readValue(json,
              WorkspaceLifecycleEvent.class);
      assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void unknownEventTypeFailsToDeserialize() {
      // Pin: a payload claiming an eventType that isn't registered in @JsonSubTypes
      // currently fails Jackson deserialization. If this starts silently coercing to a
      // registered subtype, the test should fail and the contract change must be
      // intentional, not accidental.
      String json = "{\"eventType\":\"NotARealEvent\",\"branch\":\"feat/T781\","
              + "\"aggregateType\":\"workspace\",\"aggregateId\":\"feat/T781\"}";

      assertThatThrownBy(() -> mapper.readValue(json, WorkspaceLifecycleEvent.class))
              .isInstanceOf(tools.jackson.databind.exc.MismatchedInputException.class);
    }
  }
}
