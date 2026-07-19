List<DslObject> define() {
  return Dsl.process("SimpleOrder")
      .parameters(reg -> reg.string("orderId"))
      .execute(ctx -> {
        var params = ctx.body();
        return Result.success(MapOutput.of(
            "orderId", params.values().get("orderId"),
            "message", "Order " + params.values().get("orderId") + " confirmed (runId=" + ctx.runId() + ")"));
      })
      .compensation(ctx -> {
        ctx.log("compensating SimpleOrder: " + ctx.error().getMessage());
        return Result.success(MapOutput.of("status", "COMPENSATED"));
      })
      .buildList();
}
