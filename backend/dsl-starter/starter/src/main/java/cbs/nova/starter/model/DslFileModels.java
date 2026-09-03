package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public final class DslFileModels {

  public record FileContentRequest(
          String path,
          String content) {
  }

  public record FileContentResponse(
          String path,
          String content,
          boolean pending) {
  }

  public record FileEntry(
          String path,
          long sizeBytes,
          long lastModifiedMillis) {
  }

  public record BulkWriteRequest(
          List<FileContentRequest> files) {
  }

  public record BulkWriteResult(
          int staged,
          int failed,
          @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> errors) {
  }

  public record PendingWritesStatus(
          int pending) {
  }

  public record FlushResult(
          int flushed,
          int failed,
          @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> errors) {
  }

  private DslFileModels() {
  }
}
