import cbs.nova.dslexamples.PricingModels.*;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.MathIn;
import cbs.nova.starter.helper.model.MathOut;
import java.util.List;

List<DslObject> define() {
  var lineTotalFn = Dsl.function("lineTotalFn")
      .input(OrderIn.class)
      .output(LineTotalsOut.class)
      .execute(ctx -> {
        OrderIn in = ctx.body();
        List<Number> lineTotals = in.lines().stream()
            .<Number>map(line -> line.quantity() * line.unitPrice())
            .toList();
        var summed = callHelper(ctx, "math",
            new MathIn("sum", lineTotals, null, null, null, null, null));
        if (!summed.isSuccess()) {
          return Result.failure(summed.cause());
        }
        double subtotal = ((Number) summed.as(MathOut.class).result()).doubleValue();
        return Result.success(new LineTotalsOut(subtotal));
      })
      .build();

  var orderPricingFn = Dsl.function("orderPricingFn")
      .input(OrderIn.class)
      .output(PricedOrder.class)
      .execute(ctx -> {
        OrderIn in = ctx.body();
        var totals = callFunction(ctx, "lineTotalFn", in);
        if (!totals.isSuccess()) {
          return Result.failure(totals.cause());
        }
        double subtotal = totals.as(LineTotalsOut.class).subtotal();

        double discountRate = "VIP".equalsIgnoreCase(in.customerTier()) ? 0.10 : 0.0;
        var discount = round2(ctx, subtotal * discountRate);
        if (!discount.isSuccess()) {
          return Result.failure(discount.cause());
        }
        double discountAmount = discount.value();

        double taxable = subtotal - discountAmount;
        var tax = round2(ctx, taxable * 0.20);
        if (!tax.isSuccess()) {
          return Result.failure(tax.cause());
        }
        double taxAmount = tax.value();

        return Result.success(new PricedOrder(
            in.orderId(), subtotal, discountRate, discountAmount, taxAmount,
            taxable + taxAmount));
      })
      .build();

  var checkoutProcess = Dsl.process("CheckoutProcess")
      .input(OrderIn.class)
      .output(CheckoutReceipt.class)
      .execute(ctx -> {
        OrderIn in = ctx.body();
        var priced = callFunction(ctx, "orderPricingFn", in);
        if (!priced.isSuccess()) {
          return Result.failure(priced.cause());
        }
        PricedOrder order = priced.as(PricedOrder.class);
        return Result.success(new CheckoutReceipt(
            in.orderId(),
            "RCPT-" + in.orderId(),
            order.subtotal(),
            order.discountAmount(),
            order.tax(),
            order.total()));
      })
      .build();

  var quoteTransaction = Dsl.transaction("QuoteTransaction")
      .input(OrderIn.class)
      .output(QuoteOut.class)
      .startToCloseTimeout(Duration.ofSeconds(10))
      .execute(ctx -> {
        OrderIn in = ctx.body();
        var priced = callFunction(ctx, "orderPricingFn", in);
        if (!priced.isSuccess()) {
          return Result.failure(priced.cause());
        }
        PricedOrder order = priced.as(PricedOrder.class);
        return Result.success(new QuoteOut(
            in.orderId(),
            "QUOTE-" + in.orderId(),
            order.subtotal(),
            order.discountAmount(),
            order.tax(),
            order.total()));
      })
      .build();

  return List.of(lineTotalFn, orderPricingFn, checkoutProcess, quoteTransaction);
}

// Rich contexts expose only Map-based runHelper overloads, and functions are dispatched
// through GlobalManager.runFunction (not the helper path), so typed calls — other Functions
// via callFunction, typed helpers like `math` via callHelper — build a context carrying the
// typed input body and go through GlobalManager directly.
private static Result<?> callFunction(Context<?> ctx, String name, Object input) {
  var callCtx = new ContextFactory().of(input, ctx.mode(), ctx.runId());
  return GlobalManager.globalManager().runFunction(name, callCtx);
}

private static Result<?> callHelper(Context<?> ctx, String name, Object input) {
  var callCtx = new ContextFactory().of(input, ctx.mode(), ctx.runId());
  return GlobalManager.globalManager().runHelper(name, callCtx);
}

private static Result<Double> round2(Context<?> ctx, double value) {
  var rounded = callHelper(ctx, "math",
      new MathIn("round", null, value, null, null, 2, null));
  if (!rounded.isSuccess()) {
    return Result.failure(rounded.cause());
  }
  return Result.success(((Number) rounded.as(MathOut.class).result()).doubleValue());
}
