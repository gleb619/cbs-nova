import cbs.nova.app.dsl.OrderValidationIn;
import cbs.nova.app.dsl.OrderValidationOut;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import java.math.BigDecimal;
import java.util.List;

List<DslObject> define() {
  return List.of(
    Dsl.transaction("VALIDATE_ORDER")
      .taskQueue("order-validation")
      .input(OrderValidationIn.class)
      .output(OrderValidationOut.class)
      .execute(ctx -> {
        OrderValidationIn in = ctx.body();
        boolean valid = in.amount().compareTo(BigDecimal.ZERO) > 0
            && !in.customerId().isBlank();
        return Result.success(new OrderValidationOut(valid, valid ? "ok" : "invalid input"));
      })
      .build()
  );
}
