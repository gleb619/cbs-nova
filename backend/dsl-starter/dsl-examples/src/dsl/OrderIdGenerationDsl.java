import cbs.nova.dslexamples.OrderIdGenerationModels.*;
import cbs.nova.starter.helper.model.UuidV7In;
import cbs.nova.starter.helper.model.UuidV7Out;


List<DslObject> define() {
  return Dsl.process("OrderIdGeneration")
      .input(OrderIdGenerationIn.class)
      .output(OrderIdGenerationOut.class)
      .execute(ctx -> {
        OrderIdGenerationIn in = ctx.body();

        var random = ctx.runHelper("uuidV7", new UuidV7In(null));
        if (!random.isSuccess()) {
          return Result.failure(random.cause());
        }
        String randomId = random.as(UuidV7Out.class).uuid();

        var namespaced1 = ctx.runHelper("uuidV7", new UuidV7In(in.namespace()));
        if (!namespaced1.isSuccess()) {
          return Result.failure(namespaced1.cause());
        }
        String id1 = namespaced1.as(UuidV7Out.class).uuid();

        var namespaced2 = ctx.runHelper("uuidV7", new UuidV7In(in.namespace()));
        if (!namespaced2.isSuccess()) {
          return Result.failure(namespaced2.cause());
        }
        String id2 = namespaced2.as(UuidV7Out.class).uuid();

        String tail1 = id1.substring(id1.lastIndexOf('-') + 1);
        String tail2 = id2.substring(id2.lastIndexOf('-') + 1);
        boolean deterministicTailMatch = tail1.equals(tail2);

        return Result.success(new OrderIdGenerationOut(
            in.orderId(), randomId, id1, id2, deterministicTailMatch));
      })
      .buildList();
}
