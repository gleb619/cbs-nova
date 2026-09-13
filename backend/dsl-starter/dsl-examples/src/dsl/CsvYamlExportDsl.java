import cbs.nova.dslexamples.CsvYamlExportModels.*;
import cbs.nova.starter.helper.model.FormatCsvIn;
import cbs.nova.starter.helper.model.FormatCsvOut;
import cbs.nova.starter.helper.model.ParseCsvIn;
import cbs.nova.starter.helper.model.ParseCsvOut;
import cbs.nova.starter.helper.model.ParseYamlIn;
import cbs.nova.starter.helper.model.ParseYamlOut;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("CsvYamlExport")
      .input(ExportIn.class)
      .output(ExportOut.class)
      .execute(ctx -> {
        ExportIn in = ctx.body();

        var csv = ctx.runHelper("parseCsv",
            new ParseCsvIn(in.csvPayload(), in.csvOptions()));
        if (!csv.isSuccess()) {
          return Result.failure(csv.cause());
        }
        ParseCsvOut csvOut = csv.as(ParseCsvOut.class);

        var yaml = ctx.runHelper("parseYaml", new ParseYamlIn(in.yamlPayload()));
        if (!yaml.isSuccess()) {
          return Result.failure(yaml.cause());
        }
        ParseYamlOut yamlOut = yaml.as(ParseYamlOut.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> lookup = (Map<String, Object>) yamlOut.data();

        List<List<String>> outRows = new ArrayList<>();
        for (List<String> row : csvOut.rows()) {
          if (row == null || row.isEmpty()) {
            continue;
          }
          String code = row.get(0);
          String name = lookup.containsKey(code)
              ? String.valueOf(lookup.get(code))
              : code;
          String source = row.size() > 1 ? row.get(1) : "";
          outRows.add(List.of(code, name, source));
        }

        var formatted = ctx.runHelper("formatCsv",
            new FormatCsvIn(outRows, List.of("Code", "Name", "Source"), in.csvOptions()));
        if (!formatted.isSuccess()) {
          return Result.failure(formatted.cause());
        }

        return Result.success(new ExportOut(formatted.as(FormatCsvOut.class).csv()));
      })
      .buildList();
}
