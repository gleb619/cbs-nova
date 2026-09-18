import cbs.nova.dslexamples.UnreliableApiModels.*;


List<DslObject> define() {
  var resilientTx = Dsl.transaction("unreliableApiTxResilient")
      .input(UnreliableApiInDsl.class)
      .output(String.class)
      .taskQueue("unreliable-api-queue")
      .startToCloseTimeout(Duration.ofSeconds(5))
      .retryPolicy(new RetryPolicy(4, Duration.ofMillis(200), 1.0))
      .description("A transaction that expects temporary failures. It calls the unreliable API helper and retries up to 4 times with exponential backoff. If all retries fail, it runs compensation logic.")
      .explainVia("unreliable-api-tx-resilient.md")
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
      .description("A transaction that gives up quickly. It calls the unreliable API helper with only 1 retry. If it still fails, it runs compensation logic.")
      .explainVia("unreliable-api-tx-fragile.md")
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
      .description("A process that routes an API call through the resilient transaction. Retries are expected to heal the failure, so the process succeeds and records that retries saved the call.")
      .explainVia("unreliable-api-success.md")
      .execute(ctx -> {
        UnreliableProcessIn in = ctx.body();
        var r = ctx.runTransaction("unreliableApiTxResilient", in.apiCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new UnreliableProcessOut(in.scenario(), "SUCCESS",
            List.of("retries healed failure")));
      })
      .compensation((ctx, history) ->
          ctx.log("UnreliableApiSuccess compensated: " + ctx.error().getMessage()))
      .build();

  var compensatedProcess = Dsl.process("UnreliableApiCompensated")
      .input(UnreliableProcessIn.class)
      .taskQueue("unreliable-api-queue")
      .output(UnreliableProcessOut.class)
      .description("A process that routes an API call through the fragile transaction. The fragile transaction fails after its single retry, so the process compensates and records the failure for observability.")
      .explainVia("unreliable-api-compensated.md")
      .execute(ctx -> {
        UnreliableProcessIn in = ctx.body();
        var r = ctx.runTransaction("unreliableApiTxFragile", in.apiCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new UnreliableProcessOut(in.scenario(), "SUCCESS", List.of()));
      })
      .compensation((ctx, history) -> {
        UnreliableProcessIn in = ctx.body();
        ctx.log("UnreliableApiCompensated compensated: " + ctx.error().getMessage());
        ctx.runHelper("compensationTracker",
            Map.of("markerId", "UnreliableApiCompensated-" + in.scenario()));
      })
      .build();

  var uncaughtProcess = Dsl.process("UnreliableApiUncaught")
      .input(UnreliableProcessIn.class)
      .taskQueue("unreliable-api-queue")
      .output(UnreliableProcessOut.class)
      .description("A process that routes an API call through the fragile transaction without any compensation. If the transaction fails, the process simply returns the failure.")
      .explainVia("unreliable-api-uncaught.md")
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
