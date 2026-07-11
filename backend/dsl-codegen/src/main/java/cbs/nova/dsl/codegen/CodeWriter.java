package cbs.nova.dsl.codegen;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CodeWriter {

  public static void write(@NonNull List<GeneratedSource> sources, @NonNull Path outputDir)
          throws IOException {
    for (var source : sources) {
      var packagePath = source.packageName().replace('.', '/');
      var dir = outputDir.resolve(packagePath);
      Files.createDirectories(dir);
      Files.writeString(dir.resolve(source.className() + ".java"), source.source());
    }
  }
}
