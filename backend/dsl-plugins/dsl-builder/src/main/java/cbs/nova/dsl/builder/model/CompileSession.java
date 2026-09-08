package cbs.nova.dsl.builder.model;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.Value;

@Value
public class CompileSession {

  String id;
  Path sessionDir;
  Path projectDir;
  Path outputDir;
  Instant createdAt;

  public Path srcDir() {
    return projectDir.resolve("src");
  }

  public boolean isExpired(Duration ttl, Instant now) {
    return createdAt.plus(ttl).isBefore(now);
  }

  public void writeZip(OutputStream out) throws IOException {
    try (var zip = new ZipOutputStream(out);
            var walk = Files.walk(outputDir)) {
      for (Path file : walk.filter(Files::isRegularFile).toList()) {
        var entryName = outputDir.relativize(file).toString().replace('\\', '/');
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zip);
        zip.closeEntry();
      }
    }
  }
}
