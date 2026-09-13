package cbs.nova.starter.service;

import cbs.nova.starter.exception.DslCompilationException;
import cbs.nova.starter.model.CompileDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

//TODO: force remove, its forbidden to call compilation from a java process
@Deprecated(forRemoval = true)
public class JavaSourceCompiler {

  @Deprecated(forRemoval = true)
  public void compile(Path sourceDir, Path outputDir) throws IOException {
    List<Path> javaFiles;
    try (Stream<Path> stream = Files.walk(sourceDir)) {
      javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
    }
    if (javaFiles.isEmpty()) {
      return;
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }
    var classpath = System.getProperty("java.class.path");
    var collected = new ArrayList<CompileDiagnostic>();
    Path firstFailedFile = null;
    try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
      var options = List.of("-classpath", classpath, "-d", outputDir.toString());
      for (var file : javaFiles) {
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var unit = fm.getJavaFileObjectsFromFiles(List.of(file.toFile()));
        var task = compiler.getTask(null, fm, diagnostics, options, null, unit);
        if (!task.call()) {
          if (firstFailedFile == null) {
            firstFailedFile = file;
          }
          for (var d : diagnostics.getDiagnostics()) {
            collected.add(toCompileDiagnostic(d, file));
          }
        }
      }
    }
    if (!collected.isEmpty()) {
      throw new DslCompilationException(
              "Failed to compile DSL source: " + firstFailedFile.getFileName(), collected);
    }
  }

  @Deprecated(forRemoval = true)
  private static CompileDiagnostic toCompileDiagnostic(Diagnostic<? extends JavaFileObject> d,
          Path file) {
    var source = d.getSource();
    var sourceName = source != null ? source.getName() : file.getFileName().toString();
    var line = d.getLineNumber() == Diagnostic.NOPOS ? null : Long.valueOf(d.getLineNumber());
    var column = d.getColumnNumber() == Diagnostic.NOPOS
            ? null
            : Long.valueOf(d.getColumnNumber());
    var severity = switch (d.getKind()) {
      case WARNING, MANDATORY_WARNING -> "warning";
      default -> "error";
    };
    return new CompileDiagnostic(sourceName, line, column,
            d.getMessage(Locale.getDefault()), severity, d.getCode());
  }

}
