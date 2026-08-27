import cbs.nova.dslexamples.HttpResilienceModels.*;
import cbs.nova.starter.helper.model.HttpCallIn;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  var resilientTx = Dsl.transaction("httpCallTxResilient")
      .input(HttpCallIn.class)
      .output(String.class)
      .taskQueue("http-resilience-queue")
      .startToCloseTimeout(Duration.ofSeconds(5))
      .retryPolicy(new RetryPolicy(4, Duration.ofMillis(200), 1.0))
      .execute(ctx -> {
        var r = ctx.runHelper("httpCall");
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success("resilient-ok");
      })
      .compensation(ctx -> {
        ctx.log("httpCallTxResilient compensated");
        return Result.success("tx-compensated");
      })
      .build();

  var fragileTx = Dsl.transaction("httpCallTxFragile")
      .input(HttpCallIn.class)
      .output(String.class)
      .taskQueue("http-resilience-queue")
      .startToCloseTimeout(Duration.ofSeconds(5))
      .retryPolicy(new RetryPolicy(1, Duration.ofMillis(100), 1.0))
      .execute(ctx -> {
        var r = ctx.runHelper("httpCall");
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success("fragile-ok");
      })
      .compensation(ctx -> {
        ctx.log("httpCallTxFragile compensated");
        return Result.success("tx-compensated");
      })
      .build();

  var successProcess = Dsl.process("HttpResilienceSuccess")
      .input(HttpResilienceProcessIn.class)
      .taskQueue("http-resilience-queue")
      .output(HttpResilienceProcessOut.class)
      .execute(ctx -> {
        HttpResilienceProcessIn in = ctx.body();
        var r = ctx.runTransaction("httpCallTxResilient", in.httpCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new HttpResilienceProcessOut(in.scenario(), "SUCCESS", List.of("retries healed failure")));
      })
      .compensation(ctx -> {
        ctx.log("HttpResilienceSuccess compensated: " + ctx.error().getMessage());
        return Result.success("compensated");
      })
      .build();

  var compensatedProcess = Dsl.process("HttpResilienceCompensated")
      .input(HttpResilienceProcessIn.class)
      .taskQueue("http-resilience-queue")
      .output(HttpResilienceProcessOut.class)
      .execute(ctx -> {
        HttpResilienceProcessIn in = ctx.body();
        var r = ctx.runTransaction("httpCallTxFragile", in.httpCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new HttpResilienceProcessOut(in.scenario(), "SUCCESS", List.of()));
      })
      .compensation(ctx -> {
        HttpResilienceProcessIn in = ctx.body();
        ctx.log("HttpResilienceCompensated compensated: " + ctx.error().getMessage());
        ctx.runHelper("compensationTracker",
            Map.of("markerId", "HttpResilienceCompensated-" + in.scenario()));
        return Result.success("compensated");
      })
      .build();

  var uncaughtProcess = Dsl.process("HttpResilienceUncaught")
      .input(HttpResilienceProcessIn.class)
      .taskQueue("http-resilience-queue")
      .output(HttpResilienceProcessOut.class)
      .execute(ctx -> {
        HttpResilienceProcessIn in = ctx.body();
        var r = ctx.runTransaction("httpCallTxFragile", in.httpCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new HttpResilienceProcessOut(in.scenario(), "SUCCESS", List.of()));
      })
      .build();

  return List.of(resilientTx, fragileTx, successProcess, compensatedProcess, uncaughtProcess);
}
