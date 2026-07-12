import cbs.nova.dsl.*;
import cbs.nova.dslexamples.UnreliableApiModels.*;
import cbs.nova.starter.helpers.model.UnreliableApiIn;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

void main() {
}

List<DslObject> define() {
  var resilientTx = Dsl.transaction("unreliableApiTxResilient")
      .input(UnreliableApiIn.class)
      .taskQueue("unreliable-api-queue")
      .output(String.class)
      .startToCloseTimeout(Duration.ofSeconds(5))
      .retryPolicy(new RetryPolicy(4, Duration.ofMillis(200), 1.0))
      .execute(ctx -> {
        UnreliableApiIn in = (UnreliableApiIn) ctx.body();
        Result<?> r = ctx.runHelper("unreliableApi");
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
      .input(UnreliableApiIn.class)
      .taskQueue("unreliable-api-queue")
      .output(String.class)
      .startToCloseTimeout(Duration.ofSeconds(5))
      .retryPolicy(new RetryPolicy(1, Duration.ofMillis(100), 1.0))
      .execute(ctx -> {
        UnreliableApiIn in = (UnreliableApiIn) ctx.body();
        Result<?> r = ctx.runHelper("unreliableApi");
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
        UnreliableProcessIn in = (UnreliableProcessIn) ctx.body();
        Result<?> r = ctx.runTransaction("unreliableApiTxResilient", in.apiCall());
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
        UnreliableProcessIn in = (UnreliableProcessIn) ctx.body();
        Result<?> r = ctx.runTransaction("unreliableApiTxFragile", in.apiCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new UnreliableProcessOut(in.scenario(), "SUCCESS", List.of()));
      })
      .compensation(ctx -> {
        ctx.log("UnreliableApiCompensated compensated: " + ctx.error().getMessage());
        ctx.runHelper("compensationTracker",
            Map.of("markerId", "UnreliableApiCompensated-" + ((UnreliableProcessIn) ctx.body()).scenario()));
        return Result.success("compensated");
      })
      .build();

  var uncaughtProcess = Dsl.process("UnreliableApiUncaught")
      .input(UnreliableProcessIn.class)
      .taskQueue("unreliable-api-queue")
      .output(UnreliableProcessOut.class)
      .execute(ctx -> {
        UnreliableProcessIn in = (UnreliableProcessIn) ctx.body();
        Result<?> r = ctx.runTransaction("unreliableApiTxFragile", in.apiCall());
        if (!r.isSuccess()) {
          return Result.failure(r.cause());
        }
        return Result.success(new UnreliableProcessOut(in.scenario(), "SUCCESS", List.of()));
      })
      .build();

  return List.of(resilientTx, fragileTx, successProcess, compensatedProcess, uncaughtProcess);
}
