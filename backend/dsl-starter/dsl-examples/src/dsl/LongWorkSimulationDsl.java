import cbs.nova.dslexamples.LongWorkModels.*;


List<DslObject> define() {
  return Dsl.transaction("LongWorkSimulation")
      .input(LongWorkIn.class)
      .output(LongWorkOut.class)
      .startToCloseTimeout(Duration.ofSeconds(30))
      .heartbeatTimeout(Duration.ofSeconds(5))
      .execute(ctx -> {
        // T750 demo: a long transaction that does real work in many steps, with a
        // sleep between steps so the activity outlives a single heartbeat period.
        // The generated activity wraps this body in a periodic Temporal heartbeat
        // (cbs.nova.starter.runtime.ActivityHeartbeat) — without it Temporal would
        // cancel the activity at heartbeatTimeout; with it the transaction runs to
        // completion as long as each step takes less than the heartbeat timeout.
        LongWorkIn in = ctx.body();
        int completed = 0;
        for (int i = 0; i < in.steps(); i++) {
          if (in.stepDelayMs() > 0) {
            try {
              Thread.sleep(in.stepDelayMs());
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              return Result.failure(ie);
            }
          }
          completed++;
        }
        return Result.success(new LongWorkOut(in.taskId(), "COMPLETED", completed));
      })
      .buildList();
}