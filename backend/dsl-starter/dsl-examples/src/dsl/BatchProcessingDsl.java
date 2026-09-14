import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import cbs.nova.dslexamples.v1.BatchModels.*;
import java.util.List;
import java.util.stream.Collectors;

List<DslObject> define() {
  return Dsl.process("BatchProcessing")
      .input(BatchIn.class)
      .output(BatchOut.class)
      .explainVia("batch-processing.md")
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
