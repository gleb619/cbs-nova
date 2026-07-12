package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.UnreliableApiIn;
import cbs.nova.starter.helpers.model.UnreliableApiOut;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Helper(name = "unreliableApi")
public class UnreliableApiHelper implements Executable<UnreliableApiIn, UnreliableApiOut> {

  private final ConcurrentHashMap<String, Integer> attempts = new ConcurrentHashMap<>();

  @Override
  public @NonNull Result<UnreliableApiOut> execute(@NonNull Context<UnreliableApiIn> ctx) {
    UnreliableApiIn input = ctx.body();
    int attempt = attempts.merge(input.operationId(), 1, Integer::sum);
    boolean shouldFail = attempt <= input.failCount();
    if (input.jitter() && attempt > input.failCount()) {
      shouldFail = ThreadLocalRandom.current().nextBoolean();
    }
    if (shouldFail) {
      String reason = input.reason() != null
              ? input.reason()
              : "unreliableApi attempt %d/%d failed".formatted(attempt, input.failCount());
      return Result.failure(new RuntimeException(reason));
    }
    return Result.success(new UnreliableApiOut(attempt, "ok"));
  }
}
