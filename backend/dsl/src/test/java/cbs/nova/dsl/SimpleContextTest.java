package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleContextTest {

  @Test
  void ofSetsBodyAndMode() {
    var ctx = SimpleContext.getInstance().of("payload", ExecutionMode.RUN);
    assertThat(ctx.body()).isEqualTo("payload");
    assertThat(ctx.mode()).isEqualTo(ExecutionMode.RUN);
  }

  @Test
  void ofWithRunIdSetsRunId() {
    var ctx = SimpleContext.getInstance().of("body", ExecutionMode.PREVIEW, "my-run");
    assertThat(ctx.runId()).isEqualTo("my-run");
  }

  @Test
  void ofGeneratesRunIdWhenNotProvided() {
    var ctx = SimpleContext.getInstance().of("body", ExecutionMode.RUN);
    assertThat(ctx.runId()).startsWith("run-");
  }

  @Test
  void ofWithMetadataSetsMetadata() {
    var ctx = SimpleContext.getInstance().of("body", Map.of("k", "v"), ExecutionMode.RUN, "r1");
    assertThat(ctx.metadata()).containsEntry("k", "v");
  }

  @Test
  void withBodyReturnsNewContextWithNewBody() {
    var ctx = SimpleContext.getInstance().of("original", ExecutionMode.RUN, "r1");
    var updated = ctx.withBody("replaced");
    assertThat(updated.body()).isEqualTo("replaced");
    assertThat(updated.runId()).isEqualTo("r1");
  }

  @Test
  void withBodyDoesNotMutateOriginal() {
    var ctx = SimpleContext.getInstance().of("original", ExecutionMode.RUN, "r1");
    ctx.withBody("replaced");
    assertThat(ctx.body()).isEqualTo("original");
  }

  @Test
  void withMetadataAddsKey() {
    var ctx = SimpleContext.getInstance().of("body", ExecutionMode.RUN, "r1");
    var updated = ctx.withMetadata("x", 42);
    assertThat(updated.metadata()).containsEntry("x", 42);
  }

  @Test
  void withMetadataIsImmutable() {
    var ctx = SimpleContext.getInstance().of("body", ExecutionMode.RUN, "r1");
    var updated = ctx.withMetadata("x", 1);
    assertThat(ctx.metadata()).doesNotContainKey("x");
    assertThat(updated.metadata()).containsKey("x");
  }

  @Test
  void generateRunIdProducesUniqueIds() {
    var id1 = SimpleContext.getInstance().generateRunId();
    var id2 = SimpleContext.getInstance().generateRunId();
    assertThat(id1).isNotEqualTo(id2);
    assertThat(id1).startsWith("run-");
  }
}
