package cbs.nova.dsl.process;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.ProcessCompensationHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end preview-mode signal branching through {@link ProcessContext} (T736): a process that
 * awaits a declared signal resolves it from the seeded preview buffer instead of throwing the
 * "missing SignalAwaiter" {@link IllegalStateException}.
 */
class ProcessContextPreviewSignalTest {

  private final DefaultProcessRunner runner = new DefaultProcessRunner(
          new InMemoryTransactionExecutionRepository(), null,
          new ProcessCompensationHandler(new DefaultCompensationRegistry()));

  @BeforeEach
  void register() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager().registerProcess(Dsl.process("Awaiting")
            .input(String.class)
            .output(String.class)
            .signal("approval", Map.class)
            .execute(ctx -> {
              Map<String, Object> approval = ctx.awaitSignal("approval", Map.class);
              return Result.success("approved=" + approval.get("approved")
                      + ":seen=" + ctx.signalReceived("approval")
                      + ":peek=" + (ctx.getSignalPayload("approval", Map.class) != null));
            })
            .build());
  }

  @Test
  void previewResolvesSeededSignalAtAwaitPoint() {
    var ctx = seededCtx(new SeededSignal("approval", "{\"approved\":true}", null));

    Result<?> result = runner
            .run(GlobalManager.globalManager().findProcess("Awaiting").orElseThrow(), ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("approved=true:seen=true:peek=true");
  }

  @Test
  void previewWithoutTimelineKeepsLegacyIllegalState() {
    var ctx = SimpleContext.builder().body("in").mode(ExecutionMode.PREVIEW).runId("run-nosigs")
            .build();

    Result<?> result = runner
            .run(GlobalManager.globalManager().findProcess("Awaiting").orElseThrow(), ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).hasMessageContaining("Signal awaiting is not available");
  }

  @Test
  void previewWithTimelineButUnseededNameFailsWithSignalNotSeeded() {
    var ctx = seededCtx(SeededSignal.of("other", "{}"));

    Result<?> result = runner
            .run(GlobalManager.globalManager().findProcess("Awaiting").orElseThrow(), ctx);

    assertThat(result.isSuccess()).isFalse();
    // DefaultProcessRunner wraps any process failure in DslExecutionException.
    assertThat(result.cause()).hasRootCauseInstanceOf(SignalNotSeededException.class);
    assertThat(result.cause()).hasMessageContaining("was not seeded");
  }

  private SimpleContext<Object> seededCtx(SeededSignal seed) {
    return SimpleContext.builder().body("in").mode(ExecutionMode.PREVIEW).runId("run-signals")
            .metadata(Map.of(
                    Constants.PREVIEW_SIGNAL_SEEDS_KEY, List.of(seed),
                    Constants.SIGNAL_AWAITER_METADATA_KEY, new PreviewSignalBuffer(List.of(seed))))
            .build();
  }
}
