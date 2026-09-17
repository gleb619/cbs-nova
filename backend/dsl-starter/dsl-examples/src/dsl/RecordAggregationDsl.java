import cbs.nova.dslexamples.AggregationModels.*;
import cbs.nova.starter.helper.model.ArithmeticIn;
import cbs.nova.starter.helper.model.ArithmeticOut;
import cbs.nova.starter.helper.model.ListOpsIn;
import cbs.nova.starter.helper.model.ListOpsOut;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("RecordAggregation")
      .input(AggregationIn.class)
      .output(AggregationOut.class)
      .execute(ctx -> {
        AggregationIn in = ctx.body();

        var plucked = ctx.runHelper("listOps",
            new ListOpsIn("pluck", in.records(), null, null, in.valueField(), null));
        if (!plucked.isSuccess()) {
          return Result.failure(plucked.cause());
        }
        ListOpsOut pluckedOut = plucked.as(ListOpsOut.class);
        @SuppressWarnings("unchecked")
        List<Object> prices = (List<Object>) pluckedOut.result();
        @SuppressWarnings("unchecked")
        List<Number> numbers = (List<Number>) (List<?>) prices;

        var grouped = ctx.runHelper("listOps",
            new ListOpsIn("groupBy", in.records(), null, null, in.groupField(), null));
        if (!grouped.isSuccess()) {
          return Result.failure(grouped.cause());
        }
        ListOpsOut groupedOut = grouped.as(ListOpsOut.class);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> groups =
            (Map<String, List<Map<String, Object>>>) groupedOut.result();

        var meanResult = ctx.runHelper("arithmetic",
            new ArithmeticIn("mean", null, numbers, null, null, null, null, null, null));
        if (!meanResult.isSuccess()) {
          return Result.failure(meanResult.cause());
        }
        double mean = ((Number) meanResult.as(ArithmeticOut.class).result()).doubleValue();

        var maxResult = ctx.runHelper("arithmetic",
            new ArithmeticIn("max", null, numbers, null, null, null, null, null, null));
        if (!maxResult.isSuccess()) {
          return Result.failure(maxResult.cause());
        }
        double max = ((Number) maxResult.as(ArithmeticOut.class).result()).doubleValue();

        return Result.success(new AggregationOut(prices, groups, mean, max));
      })
      .buildList();
}
