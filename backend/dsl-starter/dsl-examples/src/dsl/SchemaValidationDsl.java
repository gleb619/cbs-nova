import cbs.nova.dslexamples.SchemaValidationModels.*;
import cbs.nova.starter.helper.model.SchemaValidateIn;
import cbs.nova.starter.helper.model.SchemaValidateOut;
import cbs.nova.starter.model.ValidationError;
import java.util.List;


List<DslObject> define() {
  return Dsl.process("SchemaValidation")
      .input(SchemaValidationIn.class)
      .output(SchemaValidationOut.class)
      .description("Validates JSON payloads against a JSON Schema using the schemaValidate helper. Demonstrates a valid payload, a multi-error invalid payload, and a fail-fast short-circuit that stops at the first violation.")
      .execute(ctx -> {
        SchemaValidationIn in = ctx.body();

        var valid = ctx.runHelper("schemaValidate",
            new SchemaValidateIn(in.validPayload(), in.schema(), null));
        if (!valid.isSuccess()) {
          return Result.failure(valid.cause());
        }
        SchemaValidateOut validOut = valid.as(SchemaValidateOut.class);

        var invalid = ctx.runHelper("schemaValidate",
            new SchemaValidateIn(in.invalidPayload(), in.schema(), null));
        if (!invalid.isSuccess()) {
          return Result.failure(invalid.cause());
        }
        SchemaValidateOut invalidOut = invalid.as(SchemaValidateOut.class);
        ValidationError firstInvalid = invalidOut.errors().isEmpty()
            ? null
            : invalidOut.errors().get(0);

        var failFast = ctx.runHelper("schemaValidate",
            new SchemaValidateIn(in.failFastPayload(), in.schema(), in.failFast()));
        if (!failFast.isSuccess()) {
          return Result.failure(failFast.cause());
        }
        SchemaValidateOut failFastOut = failFast.as(SchemaValidateOut.class);
        ValidationError firstFailFast = failFastOut.errors().isEmpty()
            ? null
            : failFastOut.errors().get(0);

        return Result.success(new SchemaValidationOut(
            in.schema(),
            validOut.valid(), validOut.errors().size(),
            invalidOut.valid(), invalidOut.errors().size(),
            firstInvalid == null ? null : firstInvalid.field(),
            firstInvalid == null ? null : firstInvalid.message(),
            failFastOut.valid(), failFastOut.errors().size(),
            firstFailFast == null ? null : firstFailFast.field(),
            firstFailFast == null ? null : firstFailFast.message()));
      })
      .buildList();
}
