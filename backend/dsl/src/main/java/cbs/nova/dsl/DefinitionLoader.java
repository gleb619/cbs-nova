package cbs.nova.dsl;

import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@Slf4j
public final class DefinitionLoader {

  public void load(@NonNull Path sourceDir, @NonNull GlobalManager gm) {
    var objects = loadObjects(sourceDir);
    for (var obj : objects) {
      register(obj, gm);
    }
  }

  public @NonNull List<DslObject> loadObjects(@NonNull Path sourceDir) {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }
    var result = new ArrayList<DslObject>();
    try {
      var outputDir = Files.createTempDirectory("dsl-compiled-");
      try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
        var javaFiles = Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .toList();
        if (javaFiles.isEmpty())
          return result;
        var classpath = System.getProperty("java.class.path");
        var options = List.of("-classpath", classpath, "-d", outputDir.toString());
        for (var file : javaFiles) {
          var diagnostics = new DiagnosticCollector<JavaFileObject>();
          var singleUnit = fm.getJavaFileObjectsFromFiles(List.of(file));
          var task = compiler.getTask(null, fm, diagnostics, options, null, singleUnit);
          boolean ok = task.call();
          if (!ok) {
            diagnostics.getDiagnostics().forEach(d -> log.error("[DefinitionLoader] {}: {}",
                    file.getName(), d.getMessage(null)));
            continue;
          }
          result.addAll(loadFromFile(file, outputDir));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("DefinitionLoader.loadObjects failed", e);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private List<DslObject> loadFromFile(File source, Path outputDir) {
    String className = source.getName().replace(".java", "");
    try (var loader = new URLClassLoader(
            new URL[]{outputDir.toUri().toURL()},
            Thread.currentThread().getContextClassLoader())) {
      Class<?> cls = loader.loadClass(className);
      var ctor = cls.getDeclaredConstructor();
      ctor.setAccessible(true);
      Object instance = ctor.newInstance();
      var define = cls.getDeclaredMethod("define");
      define.setAccessible(true);
      return (List<DslObject>) define.invoke(instance);
    } catch (Exception e) {
      log.error("[DefinitionLoader] Failed to load {}: {}", className, e.getMessage(), e);
      return List.of();
    }
  }

  private void register(DslObject obj, GlobalManager gm) {
    switch (obj.type()) {
      case PROCESS -> gm.registerProcess((ProcessDslObject) obj);
      case TRANSACTION -> gm.registerTransaction((TransactionDslObject) obj);
      case FUNCTION -> gm.registerFunction((FunctionDslObject) obj);
    }
  }
}
