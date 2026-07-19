import cbs.nova.dslexamples.BatchModels.*;


List<DslObject> define() {
  return Dsl.process("BatchProcessing")
      .input(BatchIn.class)
      .output(BatchOut.class)
      .execute(ctx -> {
        BatchIn in = ctx.body();
        int total = 0;
        for (BatchItem item : in.items()) {
          total += item.value();
        }
        String summary = in.items().stream()
            .map(i -> i.id() + "=" + i.value())
            .collect(Collectors.joining(", "));
        return Result.success(new BatchOut(total, "Processed: " + summary));
      })
      .buildList();
}
