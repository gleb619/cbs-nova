package cbs.nova.dsl.codegen;

import cbs.nova.dsl.codegen.model.GeneratedSource;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class CodeWriter {

  public void write(@NonNull Path file, @NonNull String content) throws IOException {
    var parent = file.getParent();
    if (parent != null) {
      createDirectories(parent);
    }
    Files.writeString(file, content);
    log.atLevel(Level.TRACE)
            .log(() -> "[CodeWriter] Wrote %d chars to %s".formatted(content.length(), file));
  }

  public void createDirectories(@NonNull Path dir) throws IOException {
    Files.createDirectories(dir);
    log.atLevel(Level.TRACE).log(() -> "[CodeWriter] Created dir %s".formatted(dir));
  }

  public void write(@NonNull List<GeneratedSource> sources, @NonNull Path outputDir)
          throws IOException {
    log.atLevel(Level.TRACE).log(
            () -> "[CodeWriter] Writing %d sources to %s".formatted(sources.size(), outputDir));
    for (var source : sources) {
      var packagePath = source.packageName().replace('.', '/');
      var dir = outputDir.resolve(packagePath);
      createDirectories(dir);
      write(dir.resolve(source.className() + ".java"), source.source());
    }
  }

  public void writeServiceFile(
          @NonNull String serviceInterface,
          @NonNull List<String> providerFqns,
          @NonNull Path outputDir) throws IOException {
    if (providerFqns.isEmpty()) {
      return;
    }
    var serviceFile = outputDir.resolve("META-INF/services/" + serviceInterface);
    write(serviceFile, String.join(System.lineSeparator(), providerFqns)
            + System.lineSeparator());
    log.atLevel(Level.TRACE).log(() -> "[CodeWriter] Wrote service file %s".formatted(serviceFile));
  }
}
