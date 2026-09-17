package cbs.nova.dsl;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunIdPropagationTest {

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void processReceivesProvidedRunId() {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("Trace").execute(ctx -> Result.success(ctx.runId())).build());
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.PREVIEW).runId("run-xyz")
            .build();
    var result = GlobalManager.globalManager().runProcess("Trace", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("run-xyz");
  }

  @Test
  void simpleContextAutoGeneratesRunId() {
    var ctx = SimpleContext.builder().body("x").mode(ExecutionMode.PREVIEW).build();
    assertThat(ctx.runId()).startsWith("run-");
  }

  @Test
  void withBodyPreservesRunId() {
    var ctx = SimpleContext.builder().body("x").mode(ExecutionMode.PREVIEW).runId("run-123")
            .build();
    var ctx2 = ctx.withBody("y");
    assertThat(ctx2.runId()).isEqualTo("run-123");
  }

  @Test
  void withMetadataPreservesRunId() {
    var ctx = SimpleContext.builder().body("x").mode(ExecutionMode.PREVIEW).runId("run-123")
            .build();
    var ctx2 = ctx.withMetadata("k", "v");
    assertThat(ctx2.runId()).isEqualTo("run-123");
  }
}
