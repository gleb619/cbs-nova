import cbs.nova.dslexamples.RetryPolicyModels.*;
import cbs.nova.starter.helper.model.BackoffIn;
import cbs.nova.starter.helper.model.BackoffOut;
import java.util.ArrayList;


List<DslObject> define() {
  return Dsl.process("RetryPolicy")
      .input(RetryPolicyIn.class)
      .output(RetryPolicyOut.class)
      .description("Computes capped-exponential retry delays across simulated attempts with the backoff helper. It shows a deterministic schedule for jitter 'none' plus a single randomized draw for jitter 'full' so both jitter modes are visible.")
      .execute(ctx -> {
        RetryPolicyIn in = ctx.body();
        List<Long> noneDelays = new ArrayList<>();
        for (int attempt = 0; attempt < in.maxAttempts(); attempt++) {
          var r = ctx.runHelper("backoff",
              new BackoffIn(attempt, in.baseMillis(), in.maxMillis(), "none", null));
          if (!r.isSuccess()) {
            return Result.failure(r.cause());
          }
          noneDelays.add(r.as(BackoffOut.class).delayMillis());
        }
        var full = ctx.runHelper("backoff",
            new BackoffIn(in.maxAttempts(), in.baseMillis(), in.maxMillis(), "full", null));
        if (!full.isSuccess()) {
          return Result.failure(full.cause());
        }
        return Result.success(new RetryPolicyOut(
            in.maxAttempts(), in.baseMillis(), in.maxMillis(),
            noneDelays, full.as(BackoffOut.class).delayMillis()));
      })
      .buildList();
}