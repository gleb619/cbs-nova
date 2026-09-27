import cbs.nova.dslexamples.ConfigValidationModels.*;
import cbs.nova.starter.helper.model.FormatNumberIn;
import cbs.nova.starter.helper.model.FormatNumberOut;
import cbs.nova.starter.helper.model.FormatYamlIn;
import cbs.nova.starter.helper.model.FormatYamlOut;
import cbs.nova.starter.helper.model.ParseDateIn;
import cbs.nova.starter.helper.model.ParseDateOut;
import cbs.nova.starter.helper.model.ParseYamlIn;
import cbs.nova.starter.helper.model.ParseYamlOut;
import cbs.nova.starter.helper.model.RegexIn;
import cbs.nova.starter.helper.model.RegexOut;
import cbs.nova.starter.helper.model.ValidateJsonIn;
import cbs.nova.starter.helper.model.ValidateJsonOut;
import java.util.Map;


List<DslObject> define() {
  return Dsl.process("ConfigValidation")
      .input(ConfigValidationIn.class)
      .output(ConfigValidationOut.class)
      .description("Parses a YAML config blob with parseYaml, canonicalizes it with formatYaml, "
          + "structurally validates it with validateJson, checks the version field format with "
          + "regex, parses an embedded release date with parseDate, and formats a numeric "
          + "threshold with formatNumber.")
      .execute(ctx -> {
        ConfigValidationIn in = ctx.body();

        // 1) parseYaml: YAML config blob -> nested Map. parseYaml is the parse-direction
        //    helper (YAML string in, Map out); formatYaml is its emit-direction counterpart.
        var parsed = ctx.runHelper("parseYaml", new ParseYamlIn(in.configYaml()));
        if (!parsed.isSuccess()) {
          return Result.failure(parsed.cause());
        }
        Map<String, Object> config = parsed.as(ParseYamlOut.class).data();

        // 2) formatYaml: canonicalize the parsed Map back to a block-style YAML string.
        var canonical = ctx.runHelper("formatYaml", new FormatYamlIn(config));
        if (!canonical.isSuccess()) {
          return Result.failure(canonical.cause());
        }
        String canonicalYaml = canonical.as(FormatYamlOut.class).yaml();

        // 3) validateJson: structural check of the parsed config against a JSON Schema.
        //    The parsed Map is serialized to a JSON payload with avaje-jsonb.
        String payload = io.avaje.jsonb.Jsonb.builder().build().type(Object.class).toJson(config);
        var validated = ctx.runHelper("validateJson",
            new ValidateJsonIn(payload, in.schemaJson()));
        if (!validated.isSuccess()) {
          return Result.failure(validated.cause());
        }
        ValidateJsonOut validatedOut = validated.as(ValidateJsonOut.class);
        if (!validatedOut.valid()) {
          return Result.failure(new IllegalStateException(
              "config failed schema validation: " + validatedOut.errors()));
        }

        // 4) regex: the version field must look like a semver triple (e.g. "2.3.1").
        String version = String.valueOf(config.get("version"));
        var versionCheck = ctx.runHelper("regex",
            new RegexIn("match", "^\\d+\\.\\d+\\.\\d+$", version, null, null, null));
        if (!versionCheck.isSuccess()) {
          return Result.failure(versionCheck.cause());
        }
        RegexOut versionOut = versionCheck.as(RegexOut.class);

        // 5) parseDate: normalize the embedded release date to an ISO-8601 instant.
        var releaseDate = ctx.runHelper("parseDate",
            new ParseDateIn(String.valueOf(config.get("releaseDate")), "ISO_LOCAL_DATE", null));
        if (!releaseDate.isSuccess()) {
          return Result.failure(releaseDate.cause());
        }
        ParseDateOut releaseDateOut = releaseDate.as(ParseDateOut.class);

        // 6) formatNumber: render the numeric retry threshold for display.
        var threshold = ctx.runHelper("formatNumber",
            new FormatNumberIn(config.get("maxRetries"), "INTEGER", "en-US"));
        if (!threshold.isSuccess()) {
          return Result.failure(threshold.cause());
        }
        FormatNumberOut thresholdOut = threshold.as(FormatNumberOut.class);

        return Result.success(new ConfigValidationOut(
            validatedOut.valid(),
            validatedOut.errors().size(),
            Boolean.TRUE.equals(versionOut.matched()),
            releaseDateOut.iso(),
            thresholdOut.formatted(),
            canonicalYaml));
      })
      .compensation((ctx, history) ->
          ctx.log("ConfigValidation compensated: " + ctx.error().getMessage()))
      .buildList();
}
