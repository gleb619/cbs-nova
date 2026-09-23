package cbs.nova.starter.model;

public final class RequestQueryModels {

  private RequestQueryModels() {
  }

  public record ExecutionListQuery(
          String processName,
          String status,
          String mode,
          String correlationId) {
  }

  public record IntrospectionSearchQuery(
          String name,
          String type,
          String description) {
  }

  public record HelperCatalogQuery(
          String text,
          String mode) {
  }

  public record WorkingSetQuery(
          int limit,
          int offset,
          String name,
          String type,
          String description) {
  }
}
