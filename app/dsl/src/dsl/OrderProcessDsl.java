import cbs.nova.app.dsl.OrderIn;
import cbs.nova.app.dsl.OrderOut;
import cbs.nova.app.dsl.OrderValidationIn;
import cbs.nova.app.dsl.OrderValidationOut;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import java.util.List;
import java.util.UUID;

List<DslObject> define() {
  return List.of(
    Dsl.process("OrderProcess")
      .taskQueue("order-processing")
      .version("1.0.0")
      .input(OrderIn.class)
      .output(OrderOut.class)
      .execute(ctx -> {
        OrderIn in = ctx.body();

        OrderValidationOut validation = ctx.runTransaction("VALIDATE_ORDER",
            new OrderValidationIn(in.customerId(), in.amount()))
          .as(OrderValidationOut.class);

        if (!validation.valid()) {
          return Result.success(new OrderOut("N/A", "REJECTED", in.amount()));
        }

        String orderId = UUID.randomUUID().toString();
        return Result.success(new OrderOut(orderId, "ACCEPTED", in.amount()));
      })
      .build()
  );
}
