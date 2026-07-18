package cbs.nova.dsl.codegen;

import cbs.nova.dsl.codegen.model.GeneratedSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.NonNull;

public final class CodeWriter {

  public void write(@NonNull Path file, @NonNull String content) throws IOException {
    var parent = file.getParent();
    if (parent != null) {
      createDirectories(parent);
    }
    Files.writeString(file, content);
  }

  public void createDirectories(@NonNull Path dir) throws IOException {
    Files.createDirectories(dir);
  }

  public void write(@NonNull List<GeneratedSource> sources, @NonNull Path outputDir)
          throws IOException {
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
  }
}
