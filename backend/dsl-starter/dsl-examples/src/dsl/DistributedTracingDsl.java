import cbs.nova.dslexamples.DistributedTracingModels.*;
import cbs.nova.starter.helper.model.OtelIn;
import cbs.nova.starter.helper.model.OtelOut;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("DistributedTracing")
      .input(DistributedTracingIn.class)
      .output(DistributedTracingOut.class)
      .description("Demonstrates all seven OpenTelemetryHelper modes: start a span, attach an event, store/read local baggage, inject/extract W3C trace context on a simulated HTTP carrier, end the span, and return the traceparent, extracted span-id, and baggage value.")
      .execute(ctx -> {
        DistributedTracingIn in = ctx.body();

        // Note: helper-initiated spans are independent top-level spans. There is no
        // parent-child chaining across helper calls because the helper does not thread
        // ambient OTel context between invocations (documented helper limitation).
        var spanResult = ctx.runHelper("otel", new OtelIn(
            "span", "distributed-trace-example", null, null, null, null, null, null, null, null));
        if (!spanResult.isSuccess()) {
          return Result.failure(spanResult.cause());
        }
        String traceparent = (String) spanResult.as(OtelOut.class).result();

        var eventResult = ctx.runHelper("otel", new OtelIn(
            "addEvent", null, null, traceparent, null, null, "example.event",
            null, null, null));
        if (!eventResult.isSuccess()) {
          return Result.failure(eventResult.cause());
        }

        var setBaggageResult = ctx.runHelper("otel", new OtelIn(
            "setBaggage", null, null, null, null, null, null,
            "businessKey", in.businessKey(), null));
        if (!setBaggageResult.isSuccess()) {
          return Result.failure(setBaggageResult.cause());
        }

        // Simulated outbound HTTP carrier. In a real Temporal activity the W3C
        // propagator would write traceparent from the ambient OTel context; here we
        // seed the carrier with the span we just started so the example output is
        // meaningful even when run outside a propagated activity context.
        Map<String, String> outbound = Map.of("traceparent", traceparent);
        var injectResult = ctx.runHelper("otel", new OtelIn(
            "injectContext", null, null, null, null, null, null, null, null, outbound));
        if (!injectResult.isSuccess()) {
          return Result.failure(injectResult.cause());
        }

        var extractResult = ctx.runHelper("otel", new OtelIn(
            "extractContext", null, null, null, null, null, null, null, null, outbound));
        if (!extractResult.isSuccess()) {
          return Result.failure(extractResult.cause());
        }
        String extractedSpanId = (String) extractResult.as(OtelOut.class).result();

        var getBaggageResult = ctx.runHelper("otel", new OtelIn(
            "getBaggage", null, null, null, null, null, null,
            "businessKey", null, null));
        if (!getBaggageResult.isSuccess()) {
          return Result.failure(getBaggageResult.cause());
        }
        String baggageValue = (String) getBaggageResult.as(OtelOut.class).result();

        var endResult = ctx.runHelper("otel", new OtelIn(
            "endSpan", null, null, traceparent, "OK", null, null, null, null, null));
        if (!endResult.isSuccess()) {
          return Result.failure(endResult.cause());
        }

        return Result.success(new DistributedTracingOut(traceparent, extractedSpanId, baggageValue));
      })
      .buildList();
}
