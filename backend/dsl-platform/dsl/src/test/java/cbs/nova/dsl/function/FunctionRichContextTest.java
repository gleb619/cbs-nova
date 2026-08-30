package cbs.nova.dsl.function;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.dsl.transaction.TransactionRouting;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FunctionRichContextTest {

  private static final String RUN_ID = "fn-run-id";
  private final ContextFactory contextFactory = new ContextFactory();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();
  private Context<String> delegate;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    delegate = contextFactory.of("payload", ExecutionMode.RUN, RUN_ID)
            .withExecutionTraceCollector(traceCollector);
    traceCollector.start();
  }

  @AfterEach
  void tearDown() {
    traceCollector.stop();
    GlobalManager.globalManager().resetForTests();
  }

  private FunctionRichContext<String> newContext() {
    return new FunctionRichContext<>(delegate, contextFactory);
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
    var rich = new FunctionRichContext<>(withListener, contextFactory);
    assertThat(rich.executionListener()).isSameAs(listener);
  }

  @Test
  void delegatesExecutionTraceCollector() {
    assertThat(newContext().executionTraceCollector()).isSameAs(traceCollector);
  }

  @Test
  void withBodyReturnsPlainContextNotRichContext() {
    var rich = newContext();
    Context<String> next = rich.withBody("new-body");
    assertThat(next).isNotInstanceOf(FunctionRichContext.class);
    assertThat(next.body()).isEqualTo("new-body");
  }

  @Test
  void withMetadataReturnsPlainContextNotRichContext() {
    var rich = newContext();
    Context<String> next = rich.withMetadata("k", "v");
    assertThat(next).isNotInstanceOf(FunctionRichContext.class);
    assertThat(next.metadata()).containsEntry("k", "v");
  }

  @Test
  void withTransactionRoutingWrapsInNewRichContext() {
    var rich = newContext();
    Context<String> next = rich.withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
    assertThat(next).isInstanceOf(FunctionRichContext.class);
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
    assertThat(next).isInstanceOf(FunctionRichContext.class);
    assertThat(next).isNotSameAs(rich);
    assertThat(next.executionListener()).isSameAs(listener);
    assertThat(rich.executionListener()).isNull();
  }

  @Test
  void withExecutionListenerAcceptsNull() {
    ExecutionListener listener = new ExecutionListener() {
      @Override
      public void onTransactionSuccess(@NonNull TransactionExecution execution) {
      }

      @Override
      public void onTransactionFailure(@NonNull String runId,
              @NonNull String transactionName, @NonNull Throwable cause) {
      }
    };
    var rich = newContext().withExecutionListener(listener).withExecutionListener(null);
    assertThat(rich).isInstanceOf(FunctionRichContext.class);
    assertThat(rich.executionListener()).isNull();
  }

  @Test
  void withExecutionTraceCollectorWrapsInNewRichContextAndAcceptsNull() {
    var rich = newContext();
    Context<String> next = rich.withExecutionTraceCollector(null);
    assertThat(next).isInstanceOf(FunctionRichContext.class);
    assertThat(next).isNotSameAs(rich);
    assertThat(next.executionTraceCollector()).isNull();
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
    var delegateNoTrace = contextFactory.of("payload", ExecutionMode.RUN, RUN_ID);
    var rich = new FunctionRichContext<>(delegateNoTrace, contextFactory);

    Result<?> result = rich.runHelper("noop");

    assertThat(result.isSuccess()).isTrue();
  }
}
