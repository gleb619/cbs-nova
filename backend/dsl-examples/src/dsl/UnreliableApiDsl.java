import cbs.nova.dslexamples.UnreliableApiModels.*;


List<DslObject> define() {
  var resilientTx = Dsl.transaction("unreliableApiTxResilient")
      .input(UnreliableApiInDsl.class)
      .output(String.class)
      .taskQueue("unreliable-api-queue")
      .startToCloseTimeout(Duration.ofSeconds(5))
      .retryPolicy(new RetryPolicy(4, Duration.ofMillis(200), 1.0))
      .execute(ctx -> {
        UnreliableApiInDsl in = ctx.body();
        var r = ctx.runHelper("unreliableApi");
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success("resilient-ok");
      })
      .compensation(ctx -> {
        ctx.log("unreliableApiTxResilient compensated");
        return Result.success("tx-compensated");
      })
      .build();

  var fragileTx = Dsl.transaction("unreliableApiTxFragile")
      .input(UnreliableApiInDsl.class)
      .taskQueue("unreliable-api-queue")
      .output(String.class)
      .startToCloseTimeout(Duration.ofSeconds(5))
      .retryPolicy(new RetryPolicy(1, Duration.ofMillis(100), 1.0))
      .execute(ctx -> {
        UnreliableApiInDsl in = ctx.body();
        var r = ctx.runHelper("unreliableApi");
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success("fragile-ok");
      })
      .compensation(ctx -> {
        ctx.log("unreliableApiTxFragile compensated");
        return Result.success("tx-compensated");
      })
      .build();

  var successProcess = Dsl.process("UnreliableApiSuccess")
      .input(UnreliableProcessIn.class)
      .taskQueue("unreliable-api-queue")
      .output(UnreliableProcessOut.class)
      .execute(ctx -> {
        UnreliableProcessIn in = ctx.body();
        var r = ctx.runTransaction("unreliableApiTxResilient", in.apiCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new UnreliableProcessOut(in.scenario(), "SUCCESS",
            List.of("retries healed failure")));
      })
      .compensation(ctx -> {
        ctx.log("UnreliableApiSuccess compensated: " + ctx.error().getMessage());
        return Result.success("compensated");
      })
      .build();

  var compensatedProcess = Dsl.process("UnreliableApiCompensated")
      .input(UnreliableProcessIn.class)
      .taskQueue("unreliable-api-queue")
      .output(UnreliableProcessOut.class)
      .execute(ctx -> {
        UnreliableProcessIn in = ctx.body();
        var r = ctx.runTransaction("unreliableApiTxFragile", in.apiCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new UnreliableProcessOut(in.scenario(), "SUCCESS", List.of()));
      })
      .compensation(ctx -> {
        UnreliableProcessIn in = ctx.body();
        ctx.log("UnreliableApiCompensated compensated: " + ctx.error().getMessage());
        ctx.runHelper("compensationTracker",
            Map.of("markerId", "UnreliableApiCompensated-" + in.scenario()));
        return Result.success("compensated");
      })
      .build();

  var uncaughtProcess = Dsl.process("UnreliableApiUncaught")
      .input(UnreliableProcessIn.class)
      .taskQueue("unreliable-api-queue")
      .output(UnreliableProcessOut.class)
      .execute(ctx -> {
        UnreliableProcessIn in = ctx.body();
        var r = ctx.runTransaction("unreliableApiTxFragile", in.apiCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new UnreliableProcessOut(in.scenario(), "SUCCESS", List.of()));
      })
      .build();

  return List.of(resilientTx, fragileTx, successProcess, compensatedProcess, uncaughtProcess);
}
