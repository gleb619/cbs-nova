package cbs.nova.starter.model;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode;

public final class RequestQueryModels {

  private RequestQueryModels() {
  }

  public record ExecutionListQuery(
          String processName,
          String status,
          String mode,
          String correlationId) {
  }

  public record ObjectSearchQuery(
          int page,
          int size,
          String query,
          ObjectSearchMode mode,
          DslType type) {
  }
}
