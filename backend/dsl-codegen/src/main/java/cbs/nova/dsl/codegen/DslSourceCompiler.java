package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

/**
 * Convenience facade that delegates to {@link SourceCompiler} using the system Java compiler.
 */
@Slf4j
public final class DslSourceCompiler {

  public @NonNull List<DslObject> compileAndLoad(@NonNull Path sourceDir) throws IOException {
    return compileAndLoad(sourceDir, Files.createTempDirectory("dsl-codegen-"));
  }

  public @NonNull List<DslObject> compileAndLoad(
          @NonNull Path sourceDir,
          @NonNull Path outputDir) throws IOException {
    return compileAndLoad(sourceDir, outputDir, new SourceCompiler.CompileOptions(null, null));
  }

  public @NonNull List<DslObject> compileAndLoad(
          @NonNull Path sourceDir,
          @NonNull Path outputDir,
          SourceCompiler.CompileOptions options) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }
    return new SourceCompiler().compileAndLoad(sourceDir, outputDir, compiler, options);
  }
}
