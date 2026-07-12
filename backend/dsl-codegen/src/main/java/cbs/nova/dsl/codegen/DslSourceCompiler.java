package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import javax.tools.ToolProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Convenience facade that delegates to {@link SourceCompiler} using the system Java compiler.
 */
@Slf4j
@RequiredArgsConstructor
public final class DslSourceCompiler {

  private final SourceCompiler sourceCompiler;

  public @NonNull List<DslObject> compileAndLoad(
          @NonNull Path sourceDir,
          @NonNull Path outputDir,
          SourceCompiler.CompileOptions options) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }
    return sourceCompiler.compileAndLoad(sourceDir, outputDir, compiler, options);
  }
}
