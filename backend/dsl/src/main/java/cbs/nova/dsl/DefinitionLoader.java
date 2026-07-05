package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DefinitionLoader {

  private DefinitionLoader() {
  }

  public static void load(@NonNull Path sourceDir, @NonNull GlobalManager gm) {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }

    try {
      var outputDir = Files.createTempDirectory("dsl-compiled-");
      try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
        var javaFiles = Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .toList();

        if (javaFiles.isEmpty())
          return;

        var classpath = System.getProperty("java.class.path");
        var options = List.of("-classpath", classpath, "-d", outputDir.toString());

        for (var file : javaFiles) {
          var diagnostics = new DiagnosticCollector<JavaFileObject>();
          var singleUnit = fm.getJavaFileObjectsFromFiles(List.of(file));
          var task = compiler.getTask(null, fm, diagnostics, options, null, singleUnit);
          boolean ok = task.call();
          if (!ok) {
            diagnostics
                    .getDiagnostics()
                    .forEach(
                            d -> System.err.println(
                                    "[DefinitionLoader] " + file.getName() + ": "
                                            + d.getMessage(null)));
            continue;
          }
          loadAndRegister(file, outputDir, gm);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("DefinitionLoader failed", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static void loadAndRegister(File source, Path outputDir, GlobalManager gm) {
    String className = source.getName().replace(".java", "");
    try (var loader = new URLClassLoader(
            new java.net.URL[]{outputDir.toUri().toURL()},
            Thread.currentThread().getContextClassLoader())) {
      Class<?> cls = loader.loadClass(className);
      var ctor = cls.getDeclaredConstructor();
      ctor.setAccessible(true);
      Object instance = ctor.newInstance();
      var define = cls.getDeclaredMethod("define");
      define.setAccessible(true);
      var objects = (List<DslObject>) define.invoke(instance);
      for (var obj : objects) {
        register(obj, gm);
      }
    } catch (Exception e) {
      System.err.println("[DefinitionLoader] Failed to load " + className + ": " + e.getMessage());
    }
  }

  private static void register(DslObject obj, GlobalManager gm) {
    switch (obj.type()) {
      case PROCESS -> gm.registerProcess((ProcessDslObject) obj);
      case TRANSACTION -> gm.registerTransaction((TransactionDslObject) obj);
      case FUNCTION -> gm.registerFunction((FunctionDslObject) obj);
    }
  }
}
