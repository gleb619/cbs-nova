package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Map;

class SimpleContextTest {

  @Test
  void ofSetsBodyAndMode() {
    var ctx = SimpleContext.of("payload", ExecutionMode.RUN);
    assertThat(ctx.body()).isEqualTo("payload");
    assertThat(ctx.mode()).isEqualTo(ExecutionMode.RUN);
  }

  @Test
  void ofWithRunIdSetsRunId() {
    var ctx = SimpleContext.of("body", ExecutionMode.PREVIEW, "my-run");
    assertThat(ctx.runId()).isEqualTo("my-run");
  }

  @Test
  void ofGeneratesRunIdWhenNotProvided() {
    var ctx = SimpleContext.of("body", ExecutionMode.RUN);
    assertThat(ctx.runId()).startsWith("run-");
  }

  @Test
  void ofWithMetadataSetsMetadata() {
    var ctx = SimpleContext.of("body", Map.of("k", "v"), ExecutionMode.RUN, "r1");
    assertThat(ctx.metadata()).containsEntry("k", "v");
  }

  @Test
  void withBodyReturnsNewContextWithNewBody() {
    var ctx = SimpleContext.of("original", ExecutionMode.RUN, "r1");
    var updated = ctx.withBody("replaced");
    assertThat(updated.body()).isEqualTo("replaced");
    assertThat(updated.runId()).isEqualTo("r1");
  }

  @Test
  void withBodyDoesNotMutateOriginal() {
    var ctx = SimpleContext.of("original", ExecutionMode.RUN, "r1");
    ctx.withBody("replaced");
    assertThat(ctx.body()).isEqualTo("original");
  }

  @Test
  void withMetadataAddsKey() {
    var ctx = SimpleContext.of("body", ExecutionMode.RUN, "r1");
    var updated = ctx.withMetadata("x", 42);
    assertThat(updated.metadata()).containsEntry("x", 42);
  }

  @Test
  void withMetadataIsImmutable() {
    var ctx = SimpleContext.of("body", ExecutionMode.RUN, "r1");
    var updated = ctx.withMetadata("x", 1);
    assertThat(ctx.metadata()).doesNotContainKey("x");
    assertThat(updated.metadata()).containsKey("x");
  }

  @Test
  void generateRunIdProducesUniqueIds() {
    var id1 = SimpleContext.generateRunId();
    var id2 = SimpleContext.generateRunId();
    assertThat(id1).isNotEqualTo(id2);
    assertThat(id1).startsWith("run-");
  }
}
