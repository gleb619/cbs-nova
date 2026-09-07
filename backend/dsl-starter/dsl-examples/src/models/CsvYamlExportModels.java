import cbs.nova.starter.helper.model.CsvOptions;

public class CsvYamlExportModels {

  public record ExportIn(String csvPayload, CsvOptions csvOptions, String yamlPayload) {
  }

  public record ExportOut(String csv) {
  }
}
