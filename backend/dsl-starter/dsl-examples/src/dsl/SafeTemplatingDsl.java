import cbs.nova.dslexamples.SafeTemplatingModels.*;
import cbs.nova.starter.helper.model.FormatMessageIn;
import cbs.nova.starter.helper.model.FormatMessageOut;
import cbs.nova.starter.helper.model.InterpolateIn;
import cbs.nova.starter.helper.model.InterpolateOut;


List<DslObject> define() {
  return Dsl.process("SafeTemplating")
      .input(SafeTemplatingIn.class)
      .output(SafeTemplatingOut.class)
      .execute(ctx -> {
        SafeTemplatingIn in = ctx.body();
        Map<String, Object> params = Map.of("order", in.order());

        // 1) interpolate: nested-path lookup — ${order.id} walks the nested params map.
        var nested = ctx.runHelper("interpolate",
            new InterpolateIn("Order #${order.id} placed", params, null));
        if (!nested.isSuccess()) {
          return Result.failure(nested.cause());
        }
        InterpolateOut nestedOut = nested.as(InterpolateOut.class);

        // 2) interpolate: ${key:-default} substitutes a literal default when the key is missing.
        var withDefault = ctx.runHelper("interpolate",
            new InterpolateIn("Priority: ${order.priority:-standard}", params, null));
        if (!withDefault.isSuccess()) {
          return Result.failure(withDefault.cause());
        }
        InterpolateOut defaultOut = withDefault.as(InterpolateOut.class);

        // 3) interpolate: $${...} escapes a literal '$', so the placeholder is never evaluated.
        var escaped = ctx.runHelper("interpolate",
            new InterpolateIn("Raw token: $${order.total} (kept literal)", params, null));
        if (!escaped.isSuccess()) {
          return Result.failure(escaped.cause());
        }
        InterpolateOut escapedOut = escaped.as(InterpolateOut.class);

        // 4) interpolate: missing key with onMissing="empty" so the demo doesn't fail the process.
        var missing = ctx.runHelper("interpolate",
            new InterpolateIn("Coupon: ${order.couponCode}", params, "empty"));
        if (!missing.isSuccess()) {
          return Result.failure(missing.cause());
        }
        InterpolateOut missingOut = missing.as(InterpolateOut.class);

        // 5) formatMessage: SpEL-evaluated template. SAFE HERE only because greetingTemplate is
        //    an operator-authored constant baked into this DSL source, never user/operator config
        //    at runtime. If the template text ever came from stored configuration instead, use
        //    interpolate — formatMessage's SpEL evaluation is a code-execution surface and is
        //    unsafe for untrusted or externally-editable templates (see InterpolateHelper's Javadoc).
        var formatted = ctx.runHelper("formatMessage",
            new FormatMessageIn(in.greetingTemplate(), Map.of("orderId", in.order().get("id"))));
        if (!formatted.isSuccess()) {
          return Result.failure(formatted.cause());
        }
        FormatMessageOut formattedOut = formatted.as(FormatMessageOut.class);

        return Result.success(new SafeTemplatingOut(
            nestedOut.result(),
            defaultOut.result(),
            escapedOut.result(),
            missingOut.result(),
            nestedOut.resolvedKeys(),
            formattedOut.result()));
      })
      .buildList();
}
