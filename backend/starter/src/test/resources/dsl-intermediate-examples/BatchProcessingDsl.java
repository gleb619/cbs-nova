import java.util.stream.Collectors;
import java.time.Duration;
import java.util.List;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;
import cbs.nova.dslexamples.BatchModels.BatchIn;
import cbs.nova.dslexamples.BatchModels.BatchItem;
import cbs.nova.dslexamples.BatchModels.BatchOut;

void main() {
}

List<DslObject> define() {
  return Dsl.process("BatchProcessing")
      .input(BatchIn.class)
      .output(BatchOut.class)
      .execute(ctx -> {
        BatchIn in = (BatchIn) ctx.body();
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