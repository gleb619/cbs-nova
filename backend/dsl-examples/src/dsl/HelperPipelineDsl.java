import cbs.nova.dslexamples.HelperPipelineModels.*;
import cbs.nova.starter.helpers.model.FilterRecordsIn;
import cbs.nova.starter.helpers.model.FilterRecordsOut;
import cbs.nova.starter.helpers.model.FormatMessageIn;
import cbs.nova.starter.helpers.model.FormatMessageOut;
import cbs.nova.starter.helpers.model.JsonExtractIn;
import cbs.nova.starter.helpers.model.JsonExtractOut;
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

        List<Double> amounts = matched.stream()
            .map(r -> ((Number) r.get("amount")).doubleValue())
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

        var extracted = ctx.runHelper("jsonExtract",
            new JsonExtractIn(in.payloadJson(), in.extractPath()));
        if (!extracted.isSuccess()) {
          return Result.failure(extracted.cause());
        }
        JsonExtractOut extractedOut = extracted.as(JsonExtractOut.class);

        return Result.success(new PipelineOut(
            matched.size(),
            summedOut.sum(),
            renderedOut.result(),
            extractedOut.value(),
            extractedOut.present()));
      })
      .compensation(ctx -> {
        ctx.log("HelperPipeline compensated: " + ctx.error().getMessage());
        return Result.success("pipeline compensated");
      })
      .buildList();
}
