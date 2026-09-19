import cbs.nova.dslexamples.OrderMetricsModels.*;
import cbs.nova.starter.helper.model.MetricIn;
import cbs.nova.starter.helper.model.MetricOut;


List<DslObject> define() {
  return Dsl.process("OrderMetrics")
      .input(OrderMetricsIn.class)
      .output(OrderMetricsOut.class)
      .execute(ctx -> {
        OrderMetricsIn in = ctx.body();

        long start = System.currentTimeMillis();

        // Business logic: validate and process the order.
        if (in.quantity() <= 0) {
          return Result.failure(new IllegalArgumentException("quantity must be > 0"));
        }

        long processingTimeMs = System.currentTimeMillis() - start;

        // Emit a counter: total orders processed, tagged by category.
        var counter = ctx.runHelper("metric",
            new MetricIn("counter", "orders.processed", Map.of("category", in.productCategory()),
                null, 1L, null));
        if (!counter.isSuccess()) {
          return Result.failure(counter.cause());
        }

        // Emit a timer: processing duration in milliseconds, tagged by category.
        var timer = ctx.runHelper("metric",
            new MetricIn("timer", "orders.processing.duration", Map.of("category", in.productCategory()),
                null, null, processingTimeMs));
        if (!timer.isSuccess()) {
          return Result.failure(timer.cause());
        }

        return Result.success(new OrderMetricsOut(in.orderId(), "PROCESSED", processingTimeMs));
      })
      .compensation((ctx, history) ->
          ctx.log("OrderMetrics compensated: " + ctx.error().getMessage()))
      .buildList();
}
