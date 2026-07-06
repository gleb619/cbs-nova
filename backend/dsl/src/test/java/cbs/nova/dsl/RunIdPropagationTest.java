package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunIdPropagationTest {

  @BeforeEach
  void reset() {
    GlobalManager.resetForTests();
  }

  @Test
  void processReceivesProvidedRunId() {
    GlobalManager.getInstance().registerProcess(
            Dsl.process("Trace").execute(ctx -> Result.success(ctx.runId())).build());
    var ctx = SimpleContext.of("in", ExecutionMode.PREVIEW, "run-xyz");
    var result = GlobalManager.getInstance().runProcess("Trace", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("run-xyz");
  }

  @Test
  void simpleContextAutoGeneratesRunId() {
    var ctx = SimpleContext.of("x", ExecutionMode.PREVIEW);
    assertThat(ctx.runId()).startsWith("run-");
  }

  @Test
  void withBodyPreservesRunId() {
    var ctx = SimpleContext.of("x", ExecutionMode.PREVIEW, "run-123");
    var ctx2 = ctx.withBody("y");
    assertThat(ctx2.runId()).isEqualTo("run-123");
  }

  @Test
  void withMetadataPreservesRunId() {
    var ctx = SimpleContext.of("x", ExecutionMode.PREVIEW, "run-123");
    var ctx2 = ctx.withMetadata("k", "v");
    assertThat(ctx2.runId()).isEqualTo("run-123");
  }
}
