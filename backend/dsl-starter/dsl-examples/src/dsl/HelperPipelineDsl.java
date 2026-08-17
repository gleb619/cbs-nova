import cbs.nova.dsl.JsonValue;
import cbs.nova.dslexamples.HelperPipelineModels.*;
import cbs.nova.starter.helpers.model.FilterRecordsIn;
import cbs.nova.starter.helpers.model.FilterRecordsOut;
import cbs.nova.starter.helpers.model.FormatMessageIn;
import cbs.nova.starter.helpers.model.FormatMessageOut;
import cbs.nova.starter.helpers.model.SumValuesIn;
import cbs.nova.starter.helpers.model.SumValuesOut;


List<DslObject> define() {
  return Dsl.process("HelperPipeline")
      .input(PipelineIn.class)
      .output(PipelineOut.class)
      .execute(ctx -> {
        PipelineIn in = ctx.body();

        var filtered = ctx.runHelper("filterRecords",
            new FilterRecordsIn(in.records(), in.filterField(), in.filterValue()));
        if (!filtered.isSuccess()) {
          return Result.failure(filtered.cause());
        }
        FilterRecordsOut filteredOut = filtered.as(FilterRecordsOut.class);
        List<Map<String, Object>> matched = filteredOut.matched();

        List<Number> amounts = matched.stream()
            .map(r -> (Number) r.get("amount"))
            .toList();
        var summed = ctx.runHelper("sumValues", new SumValuesIn(amounts));
        if (!summed.isSuccess()) {
          return Result.failure(summed.cause());
        }
        SumValuesOut summedOut = summed.as(SumValuesOut.class);

        var rendered = ctx.runHelper("formatMessage",
            new FormatMessageIn(in.messageTemplate(),
                Map.of("count", matched.size(), "total", summedOut.sum())));
        if (!rendered.isSuccess()) {
          return Result.failure(rendered.cause());
        }
        FormatMessageOut renderedOut = rendered.as(FormatMessageOut.class);

        JsonValue payload = ctx.asJsonValue(in.payloadJson());
        JsonValue extracted = payload;
        for (String segment : in.extractPath().split("\\.")) {
          if (segment.isBlank()) {
            continue;
          }
          if (extracted.isArray()) {
            int index;
            try {
              index = Integer.parseInt(segment);
            } catch (NumberFormatException e) {
              extracted = cbs.nova.dsl.json.JsonValues.missing();
              break;
            }
            extracted = extracted.get(index);
          } else {
            extracted = extracted.get(segment);
          }
          if (!extracted.isPresent()) {
            break;
          }
        }

        return Result.success(new PipelineOut(
            matched.size(),
            summedOut.sum().doubleValue(),
            renderedOut.result(),
            extracted.isPresent() ? extracted.asString() : null,
            extracted.isPresent()));
      })
      .compensation(ctx -> {
        ctx.log("HelperPipeline compensated: " + ctx.error().getMessage());
        return Result.success("pipeline compensated");
      })
      .buildList();
}
