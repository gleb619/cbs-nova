package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.process.PreviewSignalBuffer;
import cbs.nova.dsl.process.SeededSignal;
import cbs.nova.starter.core.pipe.DslPipeContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignalSeedStageTest {

  private final SignalSeedStage stage = new SignalSeedStage();

  @Test
  void installsPreviewBufferWhenTimelinePresent() {
    Context<?> dispatched = dispatchedContext(List.of(SeededSignal.of("approval", "{}")));

    assertThat(dispatched.metadata().get(Constants.SIGNAL_AWAITER_METADATA_KEY))
            .isInstanceOf(PreviewSignalBuffer.class);
  }

  @Test
  void leavesContextUntouchedWithoutTimeline() {
    Context<?> dispatched = dispatchedContext(null);

    assertThat(dispatched.metadata()).doesNotContainKey(Constants.SIGNAL_AWAITER_METADATA_KEY);
  }

  @Test
  void installsDistinctBufferPerRun() {
    var first = dispatchedContext(List.of(SeededSignal.of("approval", "{}")));
    var second = dispatchedContext(List.of(SeededSignal.of("approval", "{}")));

    assertThat(first.metadata().get(Constants.SIGNAL_AWAITER_METADATA_KEY))
            .isNotSameAs(second.metadata().get(Constants.SIGNAL_AWAITER_METADATA_KEY));
  }

  private Context<?> dispatchedContext(List<SeededSignal> seeds) {
    SimpleContext<Object> ctx = SimpleContext.builder().body("in").mode(ExecutionMode.PREVIEW)
            .runId("run-signal-seed")
            .metadata(seeds == null
                    ? Map.of()
                    : Map.of(Constants.PREVIEW_SIGNAL_SEEDS_KEY, seeds))
            .build();
    DslPipeContext pipeContext = DslPipeContext.of("P", ctx, ExecutionMode.PREVIEW,
            "run-signal-seed");
    Result<?> result = stage.execute(pipeContext,
            dispatched -> Result.success(dispatched.dslContext()));
    return (Context<?>) result.value();
  }
}
