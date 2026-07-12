import cbs.nova.dsl.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  return Dsl.transaction("SimpleValidation")
      .input(Map.class)
      .output(Map.class)
      .startToCloseTimeout(Duration.ofSeconds(10))
      .heartbeatTimeout(Duration.ofSeconds(5))
      .retryPolicy(new RetryPolicy(5, Duration.ofMillis(500), 2.0))
      .execute(ctx -> {
        @SuppressWarnings("unchecked")
        var payload = (Map<String, Object>) ctx.body();
        var score = ((Number) payload.getOrDefault("score", 0)).intValue();
        if (score < 0 || score > 100) {
          return Result.failure(
              new IllegalArgumentException("score out of range: " + score));
        }
        return Result.success(Map.of(
            "score", score,
            "valid", true,
            "runId", ctx.runId()));
      })
      .compensation(ctx -> {
        ctx.log("validation failed: " + ctx.error().getMessage());
        return Result.success("VALIDATION_FAILED");
      })
      .buildList();
}
