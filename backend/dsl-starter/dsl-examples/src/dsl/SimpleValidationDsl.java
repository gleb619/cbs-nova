List<DslObject> define() {
  return Dsl.transaction("SimpleValidation")
      .parameters(reg -> reg.number("score"))
      .startToCloseTimeout(Duration.ofSeconds(10))
      .heartbeatTimeout(Duration.ofSeconds(5))
      .retryPolicy(new RetryPolicy(5, Duration.ofMillis(500), 2.0))
      .execute(ctx -> {
        var payload = ctx.body();
        var score = ((Number) payload.values().getOrDefault("score", 0)).intValue();
        if (score < 0 || score > 100) {
          return Result.failure(
              new IllegalArgumentException("score out of range: " + score));
        }
        return Result.success(MapOutput.of(
            "score", score,
            "valid", true,
            "runId", ctx.runId()));
      })
      .compensation(ctx -> {
        ctx.log("validation failed: " + ctx.error().getMessage());
        return Result.success(MapOutput.of("status", "VALIDATION_FAILED"));
      })
      .buildList();
}
