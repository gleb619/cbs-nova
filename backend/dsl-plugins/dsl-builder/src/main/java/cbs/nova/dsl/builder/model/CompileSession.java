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

  /**
   * Streams the build products a DSL runtime needs to load the compiled definitions: the compiled
   * classes ({@code build/classes/java/main}, package layout) plus processed resources such as the
   * {@code META-INF/services} provider descriptors ({@code build/resources/main}). Generated
   * {@code .java} sources stay in the session workspace; consumers load classes, not sources.
   */
  public void writeZip(OutputStream out) throws IOException {
    try (var zip = new ZipOutputStream(out)) {
      writeDir(zip, classesDir());
      writeDir(zip, resourcesDir());
    }
  }

  private Path classesDir() {
    return projectDir.resolve("build").resolve("classes").resolve("java").resolve("main");
  }

  private Path resourcesDir() {
    return projectDir.resolve("build").resolve("resources").resolve("main");
  }

  private void writeDir(ZipOutputStream zip, Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      for (Path file : walk.filter(Files::isRegularFile).toList()) {
        var entryName = dir.relativize(file).toString().replace('\\', '/');
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zip);
        zip.closeEntry();
      }
    }
  }
}
