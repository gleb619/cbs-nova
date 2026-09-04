package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

public final class DslFileModels {

  public record FileContentRequest(
          String path,
          String content) {
  }

  public record FileContentResponse(
          String path,
          String content,
          boolean pending,
          long crc32) {

    public static long crc32(String content) {
      if (content == null) {
        return 0L;
      }
      CRC32 crc = new CRC32();
      crc.update(content.getBytes(StandardCharsets.UTF_8));
      return crc.getValue();
    }
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
