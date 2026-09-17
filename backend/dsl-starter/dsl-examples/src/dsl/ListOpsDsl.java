import cbs.nova.dslexamples.ListOpsModels.*;
import cbs.nova.starter.helper.model.ListOpsIn;
import cbs.nova.starter.helper.model.ListOpsOut;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("ListOps")
      .input(ListOpsDslIn.class)
      .output(ListOpsDslOut.class)
      .execute(ctx -> {
        ListOpsDslIn in = ctx.body();

        // Representative catalog of records: a category field plus a numeric field.
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(Map.of("id", "o-1", "category", "electronics", "amount", 120.50));
        records.add(Map.of("id", "o-2", "category", "books", "amount", 45.00));
        records.add(Map.of("id", "o-3", "category", "electronics", "amount", 89.99));
        records.add(Map.of("id", "o-4", "category", "toys", "amount", 25.75));
        records.add(Map.of("id", "o-5", "category", "toys", "amount", 18.00));
        records.add(Map.of("id", "o-6", "category", "electronics", "amount", 340.00));

        // Step 1: pluck every value of the category field, in record order.
        var plucked = ctx.runHelper("listOps",
            new ListOpsIn("pluck", records, null, null, in.categoryField(), null));
        if (!plucked.isSuccess()) {
          return Result.failure(plucked.cause());
        }
        List<Object> categories = asList(plucked.as(ListOpsOut.class).result());

        // Step 2: dedupe the plucked list, preserving first-seen order.
        var distinct = ctx.runHelper("listOps",
            new ListOpsIn("distinct", null, categories, null, null, null));
        if (!distinct.isSuccess()) {
          return Result.failure(distinct.cause());
        }
        List<Object> distinctCategories = asList(distinct.as(ListOpsOut.class).result());

        // Step 3: tally how many records carry each distinct category.
        var counted = ctx.runHelper("listOps",
            new ListOpsIn("countBy", records, null, null, in.countField(), null));
        if (!counted.isSuccess()) {
          return Result.failure(counted.cause());
        }
        Map<Object, Long> counts = asCountMap(counted.as(ListOpsOut.class).result());

        return Result.success(new ListOpsDslOut(categories, distinctCategories, counts));
      })
      .buildList();
}

@SuppressWarnings("unchecked")
private static List<Object> asList(Object value) {
  return (List<Object>) value;
}

@SuppressWarnings("unchecked")
private static Map<Object, Long> asCountMap(Object value) {
  return (Map<Object, Long>) value;
}
