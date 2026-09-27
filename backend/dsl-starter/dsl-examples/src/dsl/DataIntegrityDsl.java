import cbs.nova.dslexamples.DataIntegrityModels.*;
import cbs.nova.starter.helper.model.Base64In;
import cbs.nova.starter.helper.model.Base64Out;
import cbs.nova.starter.helper.model.CompressionIn;
import cbs.nova.starter.helper.model.CompressionOut;
import cbs.nova.starter.helper.model.Sha256In;
import cbs.nova.starter.helper.model.Sha256Out;


List<DslObject> define() {
  return Dsl.process("DataIntegrity")
      .input(DataIntegrityIn.class)
      .output(DataIntegrityOut.class)
      .description("Checksum pipeline: gzip-compress a payload, hash the compressed (base64-encoded) bytes with SHA-256, then base64-encode the compressed payload for transport. The result exposes both the digest and the encoded payload.")
      .execute(ctx -> {
        DataIntegrityIn in = ctx.body();

        var compressedVar = ctx.runHelper("compression",
            new CompressionIn("gzip", in.payload(), null));
        if (!compressedVar.isSuccess()) {
          return Result.failure(compressedVar.cause());
        }
        String compressed = compressedVar.as(CompressionOut.class).result();

        var digestVar = ctx.runHelper("sha256",
            new Sha256In(compressed, "hex"));
        if (!digestVar.isSuccess()) {
          return Result.failure(digestVar.cause());
        }
        String digest = digestVar.as(Sha256Out.class).result();

        var encodedVar = ctx.runHelper("base64",
            new Base64In(compressed, "encode", false));
        if (!encodedVar.isSuccess()) {
          return Result.failure(encodedVar.cause());
        }
        String encodedPayload = encodedVar.as(Base64Out.class).result();

        return Result.success(new DataIntegrityOut(digest, encodedPayload));
      })
      .compensation((ctx, history) ->
          ctx.log("DataIntegrity compensated: " + ctx.error().getMessage()))
      .buildList();
}
