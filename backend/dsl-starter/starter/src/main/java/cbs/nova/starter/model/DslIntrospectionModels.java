package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Map;

public final class DslIntrospectionModels {

  public record DefinitionMetaDto(
          String name,
          String type,
          String version,
          String taskQueue,
          String inputType,
          String outputType,
          Boolean hasCompensation,
          String description,
          @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> inputSchema,
          DefinitionStatus status,
          @JsonInclude(JsonInclude.Include.NON_NULL) String filePath) {

    public DefinitionMetaDto withDescription(String description) {
      return new DefinitionMetaDto(name, type, version, taskQueue, inputType, outputType,
              hasCompensation, description, inputSchema, status, filePath);
    }
  }

  public record NamesResponse(List<String> names) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record HelperCatalogEntry(
          String name,
          String description,
          String inputType,
          String outputType,
          boolean hasSideEffects,
          String previewBehavior) {
  }

  public record HelpersResponse(List<String> names, List<HelperCatalogEntry> helpers) {
  }

  public record ProcessDetail(
          String name,
          String version,
          String taskQueue,
          String inputType,
          String outputType,
          boolean hasCompensation,
          String description,
          Map<String, Object> inputSchema) {

    public ProcessDetail withDescription(String description) {
      return new ProcessDetail(name, version, taskQueue, inputType, outputType,
              hasCompensation, description, inputSchema);
    }
  }

  public record TransactionDetail(
          String name,
          String version,
          String taskQueue,
          String inputType,
          String outputType,
          boolean hasCompensation,
          String description,
          long startToCloseTimeoutMs,
          Map<String, Object> inputSchema) {

    public TransactionDetail withDescription(String description) {
      return new TransactionDetail(name, version, taskQueue, inputType, outputType,
              hasCompensation, description, startToCloseTimeoutMs, inputSchema);
    }
  }

  public record HelperSearchResult(
          String name,
          String type,
          String description,
          String inputType,
          String outputType) {
  }

  public record StepDto(
          String id,
          String type,
          String name,
          @JsonInclude(JsonInclude.Include.NON_NULL) String inputMapping) {
  }

  public record ConstructBodyDto(
          String name,
          String type,
          @JsonInclude(JsonInclude.Include.NON_NULL) String code,
          List<StepDto> steps) {
  }

  public record ProcessDiagramDto(
          String name,
          String format,
          String diagram) {
  }

  public enum DefinitionStatus {

    PUBLISHED("Published"), DRAFT("Draft"), MODIFIED("Modified");

    private final String value;

    DefinitionStatus(String value) {
      this.value = value;
    }

    @JsonValue
    public String value() {
      return value;
    }
  }
}
