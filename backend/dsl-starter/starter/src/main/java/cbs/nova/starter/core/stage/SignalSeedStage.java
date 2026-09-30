package cbs.nova.starter.core.stage;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.process.PreviewSignalBuffer;
import cbs.nova.dsl.process.SeededSignal;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Installs a deterministic, seeded {@link PreviewSignalBuffer} as the execution context's
 * {@link cbs.nova.dsl.process.SignalAwaiter} so {@code ctx.awaitSignal(...)} /
 * {@code ctx.getSignalPayload(...)} / {@code ctx.signalReceived(...)} work in preview, hierarchy
 * and explain mode.
 *
 * <p>
 * The buffer is created <em>per pipe run</em> (its await-point cursor is stateful), and only when
 * the caller supplied a signal timeline — an absent timeline keeps the pre-T736 behavior untouched
 * (no awaiter installed). Delivery is purely index-based, so preview never waits on wall-clock
 * time.
 *
 * <p>
 * Run mode deliberately does not use this stage: the generated Temporal workflow installs its own
 * {@code SignalBuffer} (real signals).
 */
public final class SignalSeedStage implements DslPipeStage {

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    List<SeededSignal> seeds = seedsOf(context.dslContext());
    Context<?> seeded = seeds == null || seeds.isEmpty()
            ? context.dslContext()
            : context.dslContext().withMetadata(Constants.SIGNAL_AWAITER_METADATA_KEY,
                    new PreviewSignalBuffer(seeds));
    return next.proceed(context.withDslContext(seeded));
  }

  @SuppressWarnings("unchecked")
  private static List<SeededSignal> seedsOf(@NonNull Context<?> ctx) {
    Object raw = ctx.metadata().get(Constants.PREVIEW_SIGNAL_SEEDS_KEY);
    return raw instanceof List<?> list ? (List<SeededSignal>) list : null;
  }
}
