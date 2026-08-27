package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.UnreliableApiIn;
import cbs.nova.starter.helper.model.UnreliableApiOut;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Helper(name = "unreliableApi")
public class UnreliableApiHelper implements Executable<UnreliableApiIn, UnreliableApiOut> {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
  private static final long CLEANUP_INTERVAL_SECONDS = 30;

  private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
  private final Duration ttl;
  private final ScheduledExecutorService cleanupScheduler;

  public UnreliableApiHelper() {
    this(DEFAULT_TTL);
  }

  UnreliableApiHelper(Duration ttl) {
    this.ttl = ttl;
    this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "unreliable-api-cleanup");
      t.setDaemon(true);
      return t;
    });
    this.cleanupScheduler.scheduleAtFixedRate(
            this::evictExpired, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS,
            TimeUnit.SECONDS);
  }

  @Override
  public @NonNull Result<UnreliableApiOut> execute(@NonNull Context<UnreliableApiIn> ctx) {
    UnreliableApiIn input = ctx.body();
    String operationId = input.operationId();

    AttemptState state = attempts.compute(operationId, (id, current) -> {
      int nextAttempt = current == null ? 1 : current.attempts() + 1;
      return new AttemptState(nextAttempt, Instant.now());
    });

    boolean shouldFail = shouldFail(input, state);
    if (shouldFail) {
      String reason = input.reason() != null
              ? input.reason()
              : "unreliableApi attempt %d/%d failed".formatted(state.attempts(), input.failCount());
      return Result.failure(new RuntimeException(reason));
    }
    return Result.success(new UnreliableApiOut(state.attempts(), "ok"));
  }

  private boolean shouldFail(UnreliableApiIn input, AttemptState state) {
    return switch (input.effectivePattern()) {
      case CONSECUTIVE -> consecutiveShouldFail(input, state);
      case RANDOM -> randomShouldFail(input);
    };
  }

  private static boolean consecutiveShouldFail(UnreliableApiIn input, AttemptState state) {
    if (state.attempts() <= input.failCount()) {
      return true;
    }
    return input.jitter() && ThreadLocalRandom.current().nextBoolean();
  }

  private static boolean randomShouldFail(UnreliableApiIn input) {
    int probability = Math.max(0, Math.min(100, input.failCount()));
    return ThreadLocalRandom.current().nextInt(100) < probability;
  }

  public Map<String, AttemptState> attempts() {
    return attempts;
  }

  public void reset() {
    attempts.clear();
    cleanupScheduler.shutdownNow();
  }

  void evictExpired() {
    Instant cutoff = Instant.now().minus(ttl);
    attempts.values().removeIf(s -> s.createdAt().isBefore(cutoff));
  }

  public record AttemptState(int attempts, Instant createdAt) {
  }
}
