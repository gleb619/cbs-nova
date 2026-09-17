package cbs.nova.dsl;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.helper.NoopHelperInterceptor;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.transaction.TransactionRouting;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

class SimpleContextTest {

  @Test
  void ofSetsBodyAndMode() {
    var ctx = SimpleContext.builder().body("payload").mode(ExecutionMode.RUN).build();
    assertThat(ctx.body()).isEqualTo("payload");
    assertThat(ctx.mode()).isEqualTo(ExecutionMode.RUN);
  }

  @Test
  void ofWithRunIdSetsRunId() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.PREVIEW).runId("my-run")
            .build();
    assertThat(ctx.runId()).isEqualTo("my-run");
  }

  @Test
  void ofGeneratesRunIdWhenNotProvided() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).build();
    assertThat(ctx.runId()).startsWith("run-");
  }

  @Test
  void ofWithMetadataSetsMetadata() {
    var ctx = SimpleContext.builder().body("body").metadata(Map.of("k", "v"))
            .mode(ExecutionMode.RUN).runId("r1").build();
    assertThat(ctx.metadata()).containsEntry("k", "v");
  }

  @Test
  void withBodyReturnsNewContextWithNewBody() {
    var ctx = SimpleContext.builder().body("original").mode(ExecutionMode.RUN).runId("r1").build();
    var updated = ctx.withBody("replaced");
    assertThat(updated.body()).isEqualTo("replaced");
    assertThat(updated.runId()).isEqualTo("r1");
  }

  @Test
  void withBodyDoesNotMutateOriginal() {
    var ctx = SimpleContext.builder().body("original").mode(ExecutionMode.RUN).runId("r1").build();
    ctx.withBody("replaced");
    assertThat(ctx.body()).isEqualTo("original");
  }

  @Test
  void withMetadataAddsKey() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build();
    var updated = ctx.withMetadata("x", 42);
    assertThat(updated.metadata()).containsEntry("x", 42);
  }

  @Test
  void withMetadataIsImmutable() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build();
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

  @Test
  void bodyPreservesMapInput() {
    var input = MapInput.of("a", 1, "b", 2);
    Context<MapInput> ctx = SimpleContext.<MapInput>builder().body(input).mode(ExecutionMode.RUN)
            .runId("r1").build();

    MapInput body = ctx.body();

    assertThat(body).isEqualTo(input);
    assertThat(body.values()).containsEntry("a", 1).containsEntry("b", 2);
    assertThatThrownBy(() -> body.values().put("c", 3))
            .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void bodyReturnsMapInputEachCall() {
    var input = MapInput.of("a", 1);
    Context<MapInput> ctx = SimpleContext.<MapInput>builder().body(input).mode(ExecutionMode.RUN)
            .runId("r1").build();

    MapInput first = ctx.body();
    MapInput second = ctx.body();

    assertThat(first).isEqualTo(second);
  }

  @Test
  void defaultTransactionRoutingIsLocal() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build();
    assertThat(ctx.transactionRouting()).isEqualTo(TransactionRouting.LOCAL);
  }

  @Test
  void withTransactionRoutingReturnsNewContextWithRouting() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build();
    var updated = ctx.withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(updated.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(ctx.transactionRouting()).isEqualTo(TransactionRouting.LOCAL);
  }

  @Test
  void withBodyPreservesTransactionRouting() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build()
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    var updated = ctx.withBody("replaced");
    assertThat(updated.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
  }

  @Test
  void withMetadataPreservesTransactionRouting() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build()
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    var updated = ctx.withMetadata("x", 1);
    assertThat(updated.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
  }

  @Test
  void helperInterceptorDefaultsToNoop() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build();
    assertThat(ctx.helperInterceptor()).isSameAs(NoopHelperInterceptor.INSTANCE);
  }

  @Test
  void withHelperInterceptorReturnsNewContextWithInterceptor() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build();
    HelperInterceptor interceptor = (name, c) -> Optional.empty();
    var updated = ctx.withHelperInterceptor(interceptor);
    assertThat(updated.helperInterceptor()).isSameAs(interceptor);
    assertThat(ctx.helperInterceptor()).isSameAs(NoopHelperInterceptor.INSTANCE);
  }

  @Test
  void withHelperInterceptorPreservesOtherFields() {
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build()
            .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    HelperInterceptor interceptor = (name, c) -> Optional.empty();
    var updated = ctx.withHelperInterceptor(interceptor);
    assertThat(updated.body()).isEqualTo("body");
    assertThat(updated.mode()).isEqualTo(ExecutionMode.RUN);
    assertThat(updated.runId()).isEqualTo("r1");
    assertThat(updated.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(updated.helperInterceptor()).isSameAs(interceptor);
  }

  @Test
  void withHelperInterceptorAcceptsNull() {
    HelperInterceptor interceptor = (name, c) -> Optional.empty();
    var ctx = SimpleContext.builder().body("body").mode(ExecutionMode.RUN).runId("r1").build()
            .withHelperInterceptor(interceptor);
    var cleared = ctx.withHelperInterceptor(null);
    assertThat(cleared.helperInterceptor()).isSameAs(NoopHelperInterceptor.INSTANCE);
  }
}
