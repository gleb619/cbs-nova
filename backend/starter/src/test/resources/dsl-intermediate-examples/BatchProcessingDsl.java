import cbs.nova.dsl.*;
import cbs.nova.dslmodel.*;
import java.util.List;
import java.util.stream.Collectors;

void main() {}

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