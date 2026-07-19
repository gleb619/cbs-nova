import cbs.nova.dslexamples.OrderSagaModels.*;


List<DslObject> define() {
  var inventoryTx = Dsl.transaction("reserveInventory")
      .input(OrderSagaIn.class)
      .output(InventoryReservation.class)
      .startToCloseTimeout(Duration.ofSeconds(10))
      .execute(ctx -> {
        OrderSagaIn in = (OrderSagaIn) ctx.body();
        return Result.success(new InventoryReservation(in.orderId(), in.quantity()));
      })
      .compensation(ctx -> {
        ctx.log("compensating reserveInventory");
        return Result.success("inventory released");
      })
      .build();

  var paymentTx = Dsl.transaction("chargePayment")
      .input(OrderSagaIn.class)
      .output(PaymentResult.class)
      .startToCloseTimeout(Duration.ofSeconds(10))
      .execute(ctx -> {
        OrderSagaIn in = (OrderSagaIn) ctx.body();
        return Result.success(new PaymentResult(in.orderId(), in.quantity() * 9.99, true));
      })
      .compensation(ctx -> {
        ctx.log("compensating chargePayment");
        return Result.success("payment refunded");
      })
      .build();

  var process = Dsl.process("OrderSaga")
      .input(OrderSagaIn.class)
      .output(OrderSagaOut.class)
      .execute(ctx -> {
        OrderSagaIn in = ctx.body();
        var inv = ctx.runTransaction("reserveInventory", in);
        if (!inv.isSuccess()) {
          return Result.failure(inv.cause());
        }
        var pay = ctx.runTransaction("chargePayment", in);
        if (!pay.isSuccess()) {
          return Result.failure(pay.cause());
        }
        return Result.success(new OrderSagaOut(in.orderId(), "COMPLETED", "Order processed"));
      })
      .compensation(ctx -> {
        ctx.log("OrderSaga failed: " + ctx.error().getMessage());
        return Result.success("OrderSaga compensated");
      })
      .build();

  return List.of(inventoryTx, paymentTx, process);
}
