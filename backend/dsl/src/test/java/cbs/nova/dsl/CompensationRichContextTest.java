package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompensationRichContextTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();

  private static final String RUN_ID = "test-run-id";
  private Context<String> delegate;
  private Throwable failure;

  @BeforeEach
  void setUp() {
    delegate = contextFactory.of("payload", ExecutionMode.RUN, RUN_ID);
    failure = new RuntimeException("execute failed");
    traceCollector.start();
  }

  @AfterEach
  void tearDown() {
    traceCollector.stop();
  }

  private CompensationRichContext<String> newContext() {
    return new CompensationRichContext<>(delegate, failure, traceCollector, contextFactory);
  }

  @Test
  void delegatesBody() {
    var ctx = newContext();
    assertThat(ctx.body()).isEqualTo("payload");
  }

  @Test
  void delegatesMetadata() {
    var ctx = newContext();
    assertThat(ctx.metadata()).isEqualTo(delegate.metadata());
  }

  @Test
  void delegatesMode() {
    var ctx = newContext();
    assertThat(ctx.mode()).isEqualTo(ExecutionMode.RUN);
  }

  @Test
  void delegatesRunId() {
    var ctx = newContext();
    assertThat(ctx.runId()).isEqualTo(RUN_ID);
  }

  @Test
  void errorReturnsThrowable() {
    var ctx = newContext();
    assertThat(ctx.error()).isSameAs(failure);
  }

  @Test
  void withBodyDelegatesToDelegate() {
    var ctx = newContext();
    var newCtx = ctx.withBody("new-body");
    assertThat(newCtx.body()).isEqualTo("new-body");
  }

  @Test
  void logReturnsSelf() {
    var ctx = newContext();
    var returned = ctx.log("some message");
    assertThat(returned).isSameAs(ctx);
  }

  @Test
  void logAddsToTrace() {
    var ctx = newContext();
    ctx.log("rollback done");
    assertThat(traceCollector.snapshot())
            .anyMatch(e -> e.contains("rollback done"));
  }
}
