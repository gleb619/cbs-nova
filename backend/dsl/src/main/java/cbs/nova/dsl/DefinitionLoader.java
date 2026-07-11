package cbs.nova.dsl;

import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
      var classpath = System.getProperty("java.class.path");
      try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
        var javaFiles = Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .toList();
        if (javaFiles.isEmpty()) {
          return result;
        }
        var options = List.of("-classpath", classpath, "-d", outputDir.toString());
        var compiledClassNames = new ArrayList<String>();
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
          compiledClassNames.add(file.getName().replace(".java", ""));
        }
        if (compiledClassNames.isEmpty()) {
          return result;
        }
        result.addAll(loadViaReflection(outputDir, compiledClassNames));
      }
    } catch (Exception e) {
      throw new RuntimeException("DefinitionLoader.loadObjects failed", e);
    }
    return result;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<DslObject> loadViaReflection(Path outputDir, List<String> classNames)
          throws Exception {
    var parent = Thread.currentThread().getContextClassLoader();
    try (var loader = new URLClassLoader(new URL[]{outputDir.toUri().toURL()}, parent)) {
      var collected = new ArrayList<DslObject>();
      for (var className : classNames) {
        try {
          Class<?> clazz = loader.loadClass(className);
          Method define;
          try {
            define = clazz.getDeclaredMethod("define");
          } catch (NoSuchMethodException e) {
            log.error("[DefinitionLoader] No define() method on {}", className);
            continue;
          }
          define.setAccessible(true);
          Object instance = null;
          if (!Modifier.isStatic(define.getModifiers())) {
            instance = clazz.getDeclaredConstructor().newInstance();
          }
          Object value = define.invoke(instance);
          if (!(value instanceof List<?> list)) {
            log.error("[DefinitionLoader] define() in {} did not return a List", className);
            continue;
          }
          for (var element : list) {
            if (element instanceof DslObject obj) {
              collected.add(obj);
            } else {
              log.error("[DefinitionLoader] define() in {} returned non-DslObject element: {}",
                      className,
                      element == null ? "null" : element.getClass().getName());
            }
          }
        } catch (ClassNotFoundException e) {
          log.error("[DefinitionLoader] Could not load compiled class {}: {}",
                  className, e.getMessage());
        } catch (Exception e) {
          log.error("[DefinitionLoader] Failed invoking define() in {}: {}",
                  className, e.getMessage(), e);
        }
      }
      return Collections.unmodifiableList(collected);
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
