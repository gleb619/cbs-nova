package cbs.nova.starter.model;

import cbs.nova.dsl.LoadResult;

public final class VcsModels {

  public record DraftRequest(
          String name,
          String type,
          String status,
          String version,
          String taskQueue) {

  }

  public record DraftResponse(
          String name,
          String status,
          String location,
          boolean reloaded,
          LoadResult loadResult) {

  }

  public record DraftSummary(
          String name,
          String type,
          String status,
          String version,
          long updatedAt) {

  }
}
