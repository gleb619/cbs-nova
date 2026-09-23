package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class DslIntrospectionModels {

  public enum ConstructSchemaMode {
    PREVIEW, EXPLAIN;

    public static @NonNull ConstructSchemaMode from(@Nullable String value) {
      if (value == null || value.isBlank()) {
        return PREVIEW;
      }
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "preview" -> PREVIEW;
        case "explain" -> EXPLAIN;
        default -> throw new IllegalArgumentException("Unknown schema mode: " + value);
      };
    }
  }

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
  }

  public record NamesResponse(List<String> names) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ConstructSchemaDto(
          String name,
          String type,
          String inputType,
          String outputType,
          String description,
          Map<String, Object> inputSchema,
          Map<String, Object> outputSchema) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record HelperCatalogEntry(
          String name,
          String description,
          String inputType,
          String outputType
  ) {
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

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ObjectStructureDto(
          String name,
          String type,
          List<StructureFieldDto> fields,
          List<LogicInfoDto> logic) {
  }

  public record LogicInfoDto(
          String kind,
          LogicStatus status,
          boolean required,
          String description) {
  }

  public enum LogicStatus {

    CONFIGURED("configured"), DEFAULT("default");

    private final String value;

    LogicStatus(String value) {
      this.value = value;
    }

    @JsonValue
    public String value() {
      return value;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record StructureFieldDto(
          String path,
          String value,
          String type,
          String description) {
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
