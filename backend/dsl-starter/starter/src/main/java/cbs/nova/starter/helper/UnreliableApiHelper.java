package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.UnreliableApiIn;
import cbs.nova.starter.helper.model.UnreliableApiOut;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.NonNull;

@Helper(name = "unreliableApi")
public class UnreliableApiHelper implements Executable<UnreliableApiIn, UnreliableApiOut> {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
  private static final long DEFAULT_MAX_SIZE = 10_000L;

  private final Cache<String, AttemptState> attempts;

  public UnreliableApiHelper() {
    this(DEFAULT_TTL, DEFAULT_MAX_SIZE);
  }

  UnreliableApiHelper(Duration ttl) {
    this(ttl, DEFAULT_MAX_SIZE);
  }

  UnreliableApiHelper(Duration ttl, long maxSize) {
    this.attempts = Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maxSize)
            .build();
  }

  @Override
  public @NonNull Result<UnreliableApiOut> execute(@NonNull Context<UnreliableApiIn> ctx) {
    UnreliableApiIn input = ctx.body();
    String operationId = input.operationId();

    AttemptState state = attempts.get(operationId, id -> new AttemptState(0, Instant.now()));
    AttemptState updated = new AttemptState(state.attempts() + 1, state.createdAt());
    attempts.put(operationId, updated);

    boolean shouldFail = shouldFail(input, updated);
    if (shouldFail) {
      String reason = input.reason() != null
              ? input.reason()
              : "unreliableApi attempt %d/%d failed".formatted(updated.attempts(),
                      input.failCount());
      return Result.failure(new RuntimeException(reason));
    }
    return Result.success(new UnreliableApiOut(updated.attempts(), "ok"));
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
    return new HashMap<>(attempts.asMap());
  }

  public void reset() {
    attempts.invalidateAll();
  }

  /**
   * Triggers Caffeine's maintenance pass so expired entries become visible as absent without
   * waiting for the next read or write. Intended for tests.
   */
  void cleanUp() {
    attempts.cleanUp();
  }

  public record AttemptState(int attempts, Instant createdAt) {
  }
}
