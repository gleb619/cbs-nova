package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.config.ContextFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleContextTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void ofSetsBodyAndMode() {
    var ctx = contextFactory.of("payload", ExecutionMode.RUN);
    assertThat(ctx.body()).isEqualTo("payload");
    assertThat(ctx.mode()).isEqualTo(ExecutionMode.RUN);
  }

  @Test
  void ofWithRunIdSetsRunId() {
    var ctx = contextFactory.of("body", ExecutionMode.PREVIEW, "my-run");
    assertThat(ctx.runId()).isEqualTo("my-run");
  }

  @Test
  void ofGeneratesRunIdWhenNotProvided() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN);
    assertThat(ctx.runId()).startsWith("run-");
  }

  @Test
  void ofWithMetadataSetsMetadata() {
    var ctx = contextFactory.of("body", Map.of("k", "v"), ExecutionMode.RUN, "r1");
    assertThat(ctx.metadata()).containsEntry("k", "v");
  }

  @Test
  void withBodyReturnsNewContextWithNewBody() {
    var ctx = contextFactory.of("original", ExecutionMode.RUN, "r1");
    var updated = ctx.withBody("replaced");
    assertThat(updated.body()).isEqualTo("replaced");
    assertThat(updated.runId()).isEqualTo("r1");
  }

  @Test
  void withBodyDoesNotMutateOriginal() {
    var ctx = contextFactory.of("original", ExecutionMode.RUN, "r1");
    ctx.withBody("replaced");
    assertThat(ctx.body()).isEqualTo("original");
  }

  @Test
  void withMetadataAddsKey() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1");
    var updated = ctx.withMetadata("x", 42);
    assertThat(updated.metadata()).containsEntry("x", 42);
  }

  @Test
  void withMetadataIsImmutable() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1");
    var updated = ctx.withMetadata("x", 1);
    assertThat(ctx.metadata()).doesNotContainKey("x");
    assertThat(updated.metadata()).containsKey("x");
  }

  @Test
  void generateRunIdProducesUniqueIds() {
    var id1 = contextFactory.generateRunId();
    var id2 = contextFactory.generateRunId();
    assertThat(id1).isNotEqualTo(id2);
    assertThat(id1).startsWith("run-");
  }

  @Test
  void bodyPreservesMapInput() {
    var input = MapInput.of("a", 1, "b", 2);
    Context<MapInput> ctx = contextFactory.of(input, ExecutionMode.RUN, "r1");

    MapInput body = ctx.body();

    assertThat(body).isEqualTo(input);
    assertThat(body.values()).containsEntry("a", 1).containsEntry("b", 2);
    assertThatThrownBy(() -> body.values().put("c", 3))
            .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void bodyReturnsMapInputEachCall() {
    var input = MapInput.of("a", 1);
    Context<MapInput> ctx = contextFactory.of(input, ExecutionMode.RUN, "r1");

    MapInput first = ctx.body();
    MapInput second = ctx.body();

    assertThat(first).isEqualTo(second);
  }

  @Test
  void defaultTransactionRoutingIsLocal() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1");
    assertThat(ctx.transactionRouting()).isEqualTo(TransactionRouting.LOCAL);
  }

  @Test
  void withTransactionRoutingReturnsNewContextWithRouting() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1");
    var updated = ctx.withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(updated.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(ctx.transactionRouting()).isEqualTo(TransactionRouting.LOCAL);
  }

  @Test
  void withBodyPreservesTransactionRouting() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1")
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    var updated = ctx.withBody("replaced");
    assertThat(updated.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
  }

  @Test
  void withMetadataPreservesTransactionRouting() {
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "r1")
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    var updated = ctx.withMetadata("x", 1);
    assertThat(updated.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
  }
}
