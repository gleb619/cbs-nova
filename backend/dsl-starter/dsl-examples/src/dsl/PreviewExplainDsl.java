import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dslexamples.PreviewExplainModels.*;


List<DslObject> define() {
  // Single side-effecting transaction: its execute() body increments the
  // PreviewExplainModels.PERSIST_COUNTER static counter. The smoke test uses
  // that counter to assert that preview/explain code paths never reach this
  // transaction's execute() (only run mode does).
  var persistTx = Dsl.transaction("persistPlan")
      .input(PersistPlanIn.class)
      .output(PersistPlanOut.class)
      .startToCloseTimeout(Duration.ofSeconds(10))
      .execute(ctx -> {
        PreviewExplainModels.PERSIST_COUNTER.incrementAndGet();
        PersistPlanIn in = (PersistPlanIn) ctx.body();
        return Result.success(new PersistPlanOut(in.orderId(), true));
      })
      .build();

  // Shared pricing math: inlined as a nested helper expression so the compact
  // source stays free of nested method declarations. Used by both execute() and
  // preview() so both paths produce identical plans (subtotal, discount, tax, total).
  java.util.function.Function<PreviewExplainIn, PreviewExplainOut> buildPlan = in -> {
    java.util.List<PlanLine> lines = new java.util.ArrayList<>();
    double subtotal = 0.0;
    for (LineItem l : in.lines()) {
      double extended = l.unitPrice() * l.quantity();
      subtotal += extended;
      lines.add(new PlanLine(l.sku(), l.description(), l.quantity(), l.unitPrice(), extended));
    }
    double discount = subtotal * in.discountRate();
    double taxable = subtotal - discount;
    double tax = taxable * 0.2;
    double total = taxable + tax;
    return new PreviewExplainOut(in.orderId(), lines, subtotal, discount, tax, total);
  };

  var process = Dsl.process("PreviewExplain")
      .input(PreviewExplainIn.class)
      .output(PreviewExplainOut.class)
      .description("Demonstrates a custom preview(...) that returns a computed plan"
          + " (line items + totals, no side effects) and a custom explain(...) that"
          + " builds an ExplainReport in code — no .explainVia(resource). Run mode"
          + " actually persists the plan via the persistPlan transaction; preview"
          + " and explain modes never touch it, so the PERSIST_COUNTER stays at 0.")
      .execute(ctx -> {
        PreviewExplainIn in = ctx.body();
        PreviewExplainOut plan = buildPlan.apply(in);
        // Persist the plan as a side effect. Only run mode reaches this branch
        // because preview() short-circuits before execute(), and explain() never
        // runs execute() at all.
        var ack = ctx.runTransaction("persistPlan", new PersistPlanIn(plan.orderId()));
        if (!ack.isSuccess()) {
          return Result.failure(ack.cause());
        }
        return Result.success(plan);
      })
      .preview(ctx -> {
        // Dry-run path: produce the exact same plan but skip persistPlan so
        // PERSIST_COUNTER stays at 0 — verifiable from the smoke test.
        PreviewExplainIn in = ctx.body();
        return Result.success(buildPlan.apply(in));
      })
      .explain(ctx -> {
        // Build the explain-report tree in code: one section describing the
        // process itself (computed plan + counters + helper list) and one
        // child section per reachable entity (the persistPlan transaction).
        PreviewExplainIn in = ctx.body();
        PreviewExplainOut plan = buildPlan.apply(in);
        var persistChild = ExplainReport.builder()
            .name("persistPlan")
            .description("Side-effecting transaction that records the plan."
                + " Skipped in preview and explain modes.")
            .markdown("""
                # persistPlan (transaction)

                Persists the computed plan to durable storage. Wrapped as a Temporal
                transaction with `startToCloseTimeout = 10s`. `execute()` increments
                `PreviewExplainModels.PERSIST_COUNTER` so the smoke test can assert
                preview/explain modes never reach it.

                ```
                persistPlan(PersistPlanIn) -> PersistPlanOut
                ```
                """)
            .build();
        return Result.success(
            ExplainReport.builder()
                .name("PreviewExplain")
                .description("Compute a price plan (line items, subtotal, discount, tax,"
                    + " total) and — in run mode only — persist it via the persistPlan"
                    + " transaction. Preview mode returns the same plan without"
                    + " persistence; explain mode returns an ExplainReport describing"
                    + " the process and its reachable transactions.")
                .markdown("""
                    # PreviewExplain (process)

                    Inputs:
                    ```json
                    {
                      "orderId": "%s",
                      "lines": <List<LineItem>>,
                      "discountRate": %.2f
                    }
                    ```

                    Plan (preview output):
                    - orderId: %s
                    - line count: %d
                    - subtotal: %.2f
                    - discount: %.2f
                    - tax: %.2f
                    - total: %.2f

                    Reachable entities:
                    - `persistPlan` (transaction) — write side effect, skipped in preview/explain.
                    """.formatted(
                    in.orderId(),
                    in.discountRate(),
                    plan.orderId(),
                    plan.plan().size(),
                    plan.subtotal(),
                    plan.discount(),
                    plan.tax(),
                    plan.total()))
                .children(List.of(persistChild))
                .build());
      })
      .build();

  return List.of(persistTx, process);
}