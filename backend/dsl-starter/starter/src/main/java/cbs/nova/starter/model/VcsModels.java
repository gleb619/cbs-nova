package cbs.nova.starter.model;

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
          boolean reloaded) {

  }
}
