import cbs.nova.dslexamples.SampleDataGenerationModels.*;
import cbs.nova.starter.helper.model.RandomIn;
import cbs.nova.starter.helper.model.RandomOut;
import java.util.ArrayList;
import java.util.List;


List<DslObject> define() {
  return Dsl.process("SampleDataGeneration")
      .input(SampleDataGenerationIn.class)
      .output(SampleDataGenerationOut.class)
      .description("Generates a synthetic order record using the non-cryptographic `random` helper across four modes (string, int, double, choice). The helper is backed by ThreadLocalRandom and is intended for sample data, ids, and load-test jitter only — NOT for secrets, tokens, or any security-sensitive value. Use the dedicated cryptographic helper for security-sensitive material.")
      .execute(ctx -> {
        SampleDataGenerationIn in = ctx.body();
        String prefix = (in.prefix() == null || in.prefix().isBlank()) ? "ORD" : in.prefix();

        // string mode — random alphanumeric order-suffix.
        var orderIdVar = ctx.runHelper("random",
            new RandomIn("string", null, null, null, null, null, null, 8, "alphanumeric", null));
        if (!orderIdVar.isSuccess()) {
          return Result.failure(orderIdVar.cause());
        }
        String orderId = prefix + "-" + ((String) orderIdVar.as(RandomOut.class).result());

        // int mode — customerId in [10000, 99999].
        var custVar = ctx.runHelper("random",
            new RandomIn("int", 10000, 99999, null, null, null, null, null, null, null));
        if (!custVar.isSuccess()) {
          return Result.failure(custVar.cause());
        }
        int customerId = (Integer) custVar.as(RandomOut.class).result();

        // double mode — amount in [10.0, 1000.0).
        var amountVar = ctx.runHelper("random",
            new RandomIn("double", null, null, null, null, 10.0, 1000.0, null, null, null));
        if (!amountVar.isSuccess()) {
          return Result.failure(amountVar.cause());
        }
        double amount = (Double) amountVar.as(RandomOut.class).result();

        // choice mode — priority tier from {low, medium, high}.
        var prioVar = ctx.runHelper("random",
            new RandomIn("choice", null, null, null, null, null, null, null, null,
                List.<Object>of("low", "medium", "high")));
        if (!prioVar.isSuccess()) {
          return Result.failure(prioVar.cause());
        }
        String priority = (String) prioVar.as(RandomOut.class).result();

        // choice mode — region from {EU, US, APAC}.
        var regionVar = ctx.runHelper("random",
            new RandomIn("choice", null, null, null, null, null, null, null, null,
                List.<Object>of("EU", "US", "APAC")));
        if (!regionVar.isSuccess()) {
          return Result.failure(regionVar.cause());
        }
        String region = (String) regionVar.as(RandomOut.class).result();

        // Three random hex tags via repeated string draws.
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
          var tagVar = ctx.runHelper("random",
              new RandomIn("string", null, null, null, null, null, null, 6, "hex", null));
          if (!tagVar.isSuccess()) {
            return Result.failure(tagVar.cause());
          }
          tags.add((String) tagVar.as(RandomOut.class).result());
        }

        return Result.success(new SampleDataGenerationOut(
            orderId, customerId, amount, priority, region, tags));
      })
      .buildList();
}