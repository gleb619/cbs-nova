import cbs.nova.dslexamples.JsonPatchModels.*;
import cbs.nova.starter.helper.model.JsonExtractIn;
import cbs.nova.starter.helper.model.JsonExtractOut;
import cbs.nova.starter.helper.model.JsonPatchIn;
import cbs.nova.starter.helper.model.JsonPatchOut;

List<DslObject> define() {
  return Dsl.process("JsonPatchRoundTrip")
      .input(PatchIn.class)
      .output(PatchOut.class)
      .execute(ctx -> {
        PatchIn in = ctx.body();

        var patched = ctx.runHelper("jsonPatch",
            new JsonPatchIn(in.sourceJson(), in.patchJson(), null, "apply"));
        if (!patched.isSuccess()) {
          return Result.failure(patched.cause());
        }
        JsonPatchOut patchedOut = patched.as(JsonPatchOut.class);

        var read = ctx.runHelper("jsonExtract",
            new JsonExtractIn(patchedOut.result(), in.readPath()));
        if (!read.isSuccess()) {
          return Result.failure(read.cause());
        }
        JsonExtractOut readOut = read.as(JsonExtractOut.class);

        return Result.success(new PatchOut(
            patchedOut.result(), readOut.value(), readOut.present()));
      })
      .buildList();
}
