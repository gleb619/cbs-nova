package cbs.nova.dsl.transaction;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslSaga;
import cbs.nova.dsl.NoopSaga;
import cbs.nova.dsl.listener.ExecutionListener;
import cbs.nova.dsl.listener.NoopExecutionListener;
import cbs.nova.dsl.listener.NoopExecutionTraceCollector;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.listener.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.MapInput;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionRichContextTest {

  private static final String RUN_ID = "tx-run-id";
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();
  private Context<String> delegate;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    delegate = SimpleContext.<String>builder().body("payload").mode(ExecutionMode.RUN).runId(RUN_ID)
            .build()
            .withExecutionTraceCollector(traceCollector);
    traceCollector.start();
  }

  @AfterEach
  void tearDown() {
    traceCollector.stop();
    GlobalManager.globalManager().resetForTests();
  }

  private TransactionRichContext<String> newContext() {
    return new TransactionRichContext<>(delegate);
  }

  @Test
  void delegatesBody() {
    assertThat(newContext().body()).isEqualTo("payload");
  }

  @Test
  void delegatesMetadata() {
    assertThat(newContext().metadata()).isEqualTo(delegate.metadata());
  }

  @Test
  void delegatesMode() {
    assertThat(newContext().mode()).isEqualTo(ExecutionMode.RUN);
  }

  @Test
  void delegatesRunId() {
    assertThat(newContext().runId()).isEqualTo(RUN_ID);
  }

  @Test
  void delegatesTransactionRouting() {
    assertThat(newContext().transactionRouting()).isEqualTo(TransactionRouting.LOCAL);
  }

  @Test
  void delegatesExecutionListener() {
    ExecutionListener listener = new ExecutionListener() {
      @Override
      public void onTransactionSuccess(@NonNull TransactionExecution execution) {
      }

      @Override
      public void onTransactionFailure(@NonNull String runId,
              @NonNull String transactionName, @NonNull Throwable cause) {
      }
    };
    var withListener = delegate.withExecutionListener(listener);
    var rich = new TransactionRichContext<>(withListener);
    assertThat(rich.executionListener()).isSameAs(listener);
  }

  @Test
  void delegatesSaga() {
    DslSaga saga = DslSaga.create();
    var withSaga = delegate.withSaga(saga);
    var rich = new TransactionRichContext<>(withSaga);
    assertThat(rich.saga()).isSameAs(saga);
  }

  @Test
  void delegatesExecutionTraceCollector() {
    assertThat(newContext().executionTraceCollector()).isSameAs(traceCollector);
  }

  @Test
  void withBodyReturnsPlainContextNotRichContext() {
    var rich = newContext();
    Context<String> next = rich.withBody("new-body");
    assertThat(next).isNotInstanceOf(TransactionRichContext.class);
    assertThat(next.body()).isEqualTo("new-body");
  }

  @Test
  void withMetadataReturnsPlainContextNotRichContext() {
    var rich = newContext();
    Context<String> next = rich.withMetadata("k", "v");
    assertThat(next).isNotInstanceOf(TransactionRichContext.class);
    assertThat(next.metadata()).containsEntry("k", "v");
  }

  @Test
  void withTransactionRoutingWrapsInNewRichContext() {
    var rich = newContext();
    Context<String> next = rich.withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(next).isInstanceOf(TransactionRichContext.class);
    assertThat(next).isNotSameAs(rich);
    assertThat(next.transactionRouting()).isEqualTo(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(rich.transactionRouting()).isEqualTo(TransactionRouting.LOCAL);
  }

  @Test
  void withExecutionListenerWrapsInNewRichContext() {
    ExecutionListener listener = new ExecutionListener() {
      @Override
      public void onTransactionSuccess(@NonNull TransactionExecution execution) {
      }

      @Override
      public void onTransactionFailure(@NonNull String runId,
              @NonNull String transactionName, @NonNull Throwable cause) {
      }
    };
    var rich = newContext();
    Context<String> next = rich.withExecutionListener(listener);
    assertThat(next).isInstanceOf(TransactionRichContext.class);
    assertThat(next).isNotSameAs(rich);
    assertThat(next.executionListener()).isSameAs(listener);
    assertThat(rich.executionListener()).isSameAs(NoopExecutionListener.INSTANCE);
  }

  @Test
  void withSagaWrapsInNewRichContextAndAcceptsNull() {
    var rich = newContext();
    Context<String> next = rich.withSaga(null);
    assertThat(next).isInstanceOf(TransactionRichContext.class);
    assertThat(next).isNotSameAs(rich);
    assertThat(next.saga()).isSameAs(NoopSaga.INSTANCE);
    assertThat(rich.saga()).isSameAs(NoopSaga.INSTANCE);
  }

  @Test
  void withExecutionTraceCollectorWrapsInNewRichContextAndAcceptsNull() {
    var rich = newContext();
    Context<String> next = rich.withExecutionTraceCollector(null);
    assertThat(next).isInstanceOf(TransactionRichContext.class);
    assertThat(next).isNotSameAs(rich);
    assertThat(next.executionTraceCollector()).isSameAs(NoopExecutionTraceCollector.INSTANCE);
    assertThat(rich.executionTraceCollector()).isSameAs(traceCollector);
  }

  @Test
  void runHelperDispatchesThroughGlobalManagerAndTraces() {
    GlobalManager.globalManager().registerHelper("echo",
            ctx -> Result.success("echoed:" + ctx.body()));
    var rich = newContext();

    Result<?> result = rich.runHelper("echo");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("echoed:payload");
    assertThat(traceCollector.snapshot())
            .anyMatch(e -> e.equals("called helper: echo"));
  }

  @Test
  void runHelperWithMapInputBuildsContextViaFactoryAndTraces() {
    GlobalManager.globalManager().registerHelper("cap",
            ctx -> Result.success(ctx.body().toString().toUpperCase()));
    var rich = newContext();

    Result<?> result = rich.runHelper("cap", Map.of("k", "v"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(traceCollector.snapshot())
            .anyMatch(e -> e.equals("called helper: cap"));
  }

  @Test
  void runHelperWithMapInputTypeBuildsContextViaFactoryAndTraces() {
    GlobalManager.globalManager().registerHelper("echo",
            ctx -> Result.success(ctx.body()));
    var rich = newContext();

    Result<?> result = rich.runHelper("echo", MapInput.of("k", "v"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(traceCollector.snapshot())
            .anyMatch(e -> e.equals("called helper: echo"));
  }

  @Test
  void runHelperWithoutTraceCollectorDoesNotThrow() {
    GlobalManager.globalManager().registerHelper("noop",
            ctx -> Result.success(null));
    var delegateNoTrace = SimpleContext.builder().body("payload").mode(ExecutionMode.RUN)
            .runId(RUN_ID).build();
    var rich = new TransactionRichContext<>(delegateNoTrace);

    Result<?> result = rich.runHelper("noop");

    assertThat(result.isSuccess()).isTrue();
  }
}
