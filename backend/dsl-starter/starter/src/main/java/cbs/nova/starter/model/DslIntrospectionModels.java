package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

  public enum ConstructPathType {
    PROCESSES("processes"), TRANSACTIONS("transactions"), FUNCTIONS("functions"), HELPERS(
            "helpers");

    private final String pathSegment;

    ConstructPathType(String pathSegment) {
      this.pathSegment = pathSegment;
    }

    public static @NonNull Optional<ConstructPathType> from(@Nullable String value) {
      if (value == null || value.isBlank()) {
        return Optional.empty();
      }
      String normalized = value.trim().toLowerCase(Locale.ROOT);
      for (ConstructPathType type : values()) {
        if (type.pathSegment.equals(normalized)) {
          return Optional.of(type);
        }
      }
      return Optional.empty();
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

  public record WorkingSetResponse(
          List<DefinitionMetaDto> items,
          long total,
          int offset,
          int limit) {
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
          String outputType) {
  }

  public enum ObjectSearchMode {
    EXACT, COSINE, FUZZY, ALL;

    @JsonCreator
    public static ObjectSearchMode from(@Nullable String value) {
      if (value == null || value.isBlank()) {
        return EXACT;
      }
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "exact" -> EXACT;
        case "cosine" -> COSINE;
        case "fuzzy" -> FUZZY;
        default -> EXACT;
      };
    }
  }

  public record ObjectSearchResult(
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
