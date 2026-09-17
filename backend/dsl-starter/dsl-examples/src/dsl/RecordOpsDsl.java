import cbs.nova.dslexamples.RecordOpsModels.*;
import cbs.nova.starter.helper.model.FilterRecordsIn;
import cbs.nova.starter.helper.model.FilterRecordsOut;
import cbs.nova.starter.helper.model.PickIn;
import cbs.nova.starter.helper.model.PickOut;
import cbs.nova.starter.helper.model.SortRecordsIn;
import cbs.nova.starter.helper.model.SortRecordsOut;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("RecordOps")
      .input(RecordOpsIn.class)
      .output(RecordOpsOut.class)
      .execute(ctx -> {
        RecordOpsIn in = ctx.body();

        // Representative catalog of records: a category/status-ish field plus a numeric field.
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(Map.of("id", "o-1", "category", "electronics", "amount", 120.50, "status", "active"));
        records.add(Map.of("id", "o-2", "category", "books", "amount", 45.00, "status", "active"));
        records.add(Map.of("id", "o-3", "category", "electronics", "amount", 89.99, "status", "hold"));
        records.add(Map.of("id", "o-4", "category", "toys", "amount", 25.75, "status", "active"));
        records.add(Map.of("id", "o-5", "category", "electronics", "amount", 340.00, "status", "active"));
        records.add(Map.of("id", "o-6", "category", "books", "amount", 12.50, "status", "hold"));

        // Step 1: narrow by field/value -> keep only rows in the requested category.
        var filtered = ctx.runHelper("filterRecords",
            new FilterRecordsIn(records, "category", in.category()));
        if (!filtered.isSuccess()) {
          return Result.failure(filtered.cause());
        }
        FilterRecordsOut filteredOut = filtered.as(FilterRecordsOut.class);

        // Step 2: order by a different field (numeric) asc/desc.
        var sorted = ctx.runHelper("sortRecords",
            new SortRecordsIn(filteredOut.matched(), "amount", in.ascending(), null, null));
        if (!sorted.isSuccess()) {
          return Result.failure(sorted.cause());
        }
        SortRecordsOut sortedOut = sorted.as(SortRecordsOut.class);

        // Step 3: project each row down to the shaped output keys.
        List<Map<String, Object>> projected = new ArrayList<>();
        for (Map<String, Object> row : sortedOut.records()) {
          var picked = ctx.runHelper("pick", new PickIn(row, in.pickKeys(), "pick"));
          if (!picked.isSuccess()) {
            return Result.failure(picked.cause());
          }
          projected.add(picked.as(PickOut.class).result());
        }

        return Result.success(new RecordOpsOut(filteredOut.matched(), sortedOut.records(), projected));
      })
      .buildList();
}