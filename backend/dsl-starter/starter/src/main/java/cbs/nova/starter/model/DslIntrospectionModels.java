package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
          @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> inputSchema) {
  }

  public record NamesResponse(List<String> names) {
  }

  public record ProcessDetail(
          String name,
          String version,
          String taskQueue,
          String inputType,
          String outputType,
          boolean hasCompensation,
          Map<String, Object> inputSchema) {
  }

  public record TransactionDetail(
          String name,
          String version,
          String taskQueue,
          String inputType,
          String outputType,
          boolean hasCompensation,
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
}
