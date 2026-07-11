package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompensationRichContextTest {

  private static final String RUN_ID = "test-run-id";
  private Context<String> delegate;
  private Throwable failure;

  @BeforeEach
  void setUp() {
    delegate = SimpleContext.getInstance().of("payload", ExecutionMode.RUN, RUN_ID);
    failure = new RuntimeException("execute failed");
    ExecutionTraceCollector.getInstance().start();
  }

  @AfterEach
  void tearDown() {
    ExecutionTraceCollector.getInstance().stop();
  }

  @Test
  void delegatesBody() {
    var ctx = new CompensationRichContext<>(delegate, failure);
    assertThat(ctx.body()).isEqualTo("payload");
  }

  @Test
  void delegatesMetadata() {
    var ctx = new CompensationRichContext<>(delegate, failure);
    assertThat(ctx.metadata()).isEqualTo(delegate.metadata());
  }

  @Test
  void delegatesMode() {
    var ctx = new CompensationRichContext<>(delegate, failure);
    assertThat(ctx.mode()).isEqualTo(ExecutionMode.RUN);
  }

  @Test
  void delegatesRunId() {
    var ctx = new CompensationRichContext<>(delegate, failure);
    assertThat(ctx.runId()).isEqualTo(RUN_ID);
  }

  @Test
  void errorReturnsThrowable() {
    var ctx = new CompensationRichContext<>(delegate, failure);
    assertThat(ctx.error()).isSameAs(failure);
  }

  @Test
  void withBodyDelegatesToDelegate() {
    var ctx = new CompensationRichContext<>(delegate, failure);
    var newCtx = ctx.withBody("new-body");
    assertThat(newCtx.body()).isEqualTo("new-body");
  }

  @Test
  void logReturnsSelf() {
    var ctx = new CompensationRichContext<>(delegate, failure);
    var returned = ctx.log("some message");
    assertThat(returned).isSameAs(ctx);
  }

  @Test
  void logAddsToTrace() {
    var ctx = new CompensationRichContext<>(delegate, failure);
    ctx.log("rollback done");
    assertThat(ExecutionTraceCollector.getInstance().snapshot())
            .anyMatch(e -> e.contains("rollback done"));
  }
}
