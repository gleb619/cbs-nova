package cbs.nova.dsl;

import cbs.nova.dsl.compact.CompactSourcePreprocessor;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
//TODO: @deprecated since T230, use `ServiceLoaderDslDefinitionLoader` instead
@Deprecated(forRemoval = true)
public final class CompilingDslDefinitionLoader implements DslDefinitionLoader {

  private final ServiceLoaderDslDefinitionLoader delegate;

  @Override
  public int load(@NonNull GlobalManager gm) {
    return delegate.load(gm);
  }

  @Override
  public int load(@NonNull Path sourceDir, @NonNull GlobalManager gm) {
    if (hasJavaSources(sourceDir)) {
      var objects = loadObjects(sourceDir);
      for (var obj : objects) {
        register(obj, gm);
      }
    } else {
      return delegate.load(gm);
    }

    return 0;
  }

  @Override
  public int load(@NonNull ClassLoader classLoader, @NonNull GlobalManager gm) {
    return delegate.load(classLoader, gm);
  }

  private boolean hasJavaSources(@NonNull Path sourceDir) {
    try (var stream = Files.walk(sourceDir)) {
      return stream.anyMatch(p -> p.toString().endsWith(".java"));
    } catch (IOException e) {
      log.warn("[CompilingDslDefinitionLoader] Failed to scan {}: {}", sourceDir, e.getMessage());
      return false;
    }
  }

  @Deprecated(forRemoval = true)
  private @NonNull List<DslObject> loadObjects(@NonNull Path sourceDir) {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }
    var result = new ArrayList<DslObject>();
    try {
      var outputDir = Files.createTempDirectory("dsl-compiled-");
      var classpath = System.getProperty("java.class.path");
      List<Path> javaFiles;
      try (Stream<Path> stream = Files.walk(sourceDir)) {
        javaFiles = stream
                .filter(p -> p.toString().endsWith(".java"))
                .toList();
      }
      if (javaFiles.isEmpty()) {
        return result;
      }

      var preprocessed = new ArrayList<Path>();
      for (var file : javaFiles) {
        try {
          var pp = CompactSourcePreprocessor.preprocess(
                  file.getFileName().toString(), Files.readString(file));
          var outFile = outputDir.resolve(file.getFileName());
          Files.writeString(outFile, pp.preprocessedSource());
          preprocessed.add(outFile);
        } catch (IllegalArgumentException e) {
          log.error("[CompilingDslDefinitionLoader] {}", e.getMessage());
        }
      }
      if (preprocessed.isEmpty()) {
        return result;
      }

      var compiledClassNames = new ArrayList<String>();
      try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
        var options = List.of("-classpath", classpath, "-d", outputDir.toString());
        for (var file : preprocessed) {
          var diagnostics = new DiagnosticCollector<JavaFileObject>();
          var unit = fm.getJavaFileObjectsFromFiles(List.of(file.toFile()));
          var task = compiler.getTask(null, fm, diagnostics, options, null, unit);
          if (!task.call()) {
            diagnostics.getDiagnostics().forEach(d -> log.error(
                    "[CompilingDslDefinitionLoader] {}: {}", file.getFileName(),
                    d.getMessage(null)));
            continue;
          }
          compiledClassNames.add(file.getFileName().toString().replace(".java", ""));
        }
      }
      if (!compiledClassNames.isEmpty()) {
        result.addAll(loadViaReflection(outputDir, compiledClassNames));
      }
    } catch (IOException e) {
      throw new RuntimeException("CompilingDslDefinitionLoader.loadObjects failed", e);
    }
    return Collections.unmodifiableList(result);
  }

  @Deprecated(forRemoval = true)
  private @NonNull List<DslObject> loadViaReflection(@NonNull Path outputDir,
          @NonNull List<String> classNames) {
    var parent = Thread.currentThread().getContextClassLoader();
    try (var loader = new URLClassLoader(new URL[]{outputDir.toUri().toURL()}, parent)) {
      var collected = new ArrayList<DslObject>();
      for (var className : classNames) {
        try {
          Class<?> clazz = loader.loadClass(className);
          if (!DslCompactSource.class.isAssignableFrom(clazz)) {
            log.error("[CompilingDslDefinitionLoader] {} does not implement DslCompactSource",
                    className);
            continue;
          }
          var instance = (DslCompactSource) clazz.getDeclaredConstructor().newInstance();
          collected.addAll(instance.define());
        } catch (ClassNotFoundException e) {
          log.error("[CompilingDslDefinitionLoader] Could not load compiled class {}: {}",
                  className, e.getMessage());
        } catch (Exception e) {
          log.error("[CompilingDslDefinitionLoader] Failed invoking define() in {}: {}",
                  className, e.getMessage(), e);
        }
      }
      return Collections.unmodifiableList(collected);
    } catch (IOException e) {
      throw new RuntimeException("CompilingDslDefinitionLoader.loadViaReflection failed", e);
    }
  }

  private void register(@NonNull DslObject obj, @NonNull GlobalManager gm) {
    switch (obj.type()) {
      case PROCESS -> gm.registerProcess((ProcessDslObject) obj);
      case TRANSACTION -> gm.registerTransaction((TransactionDslObject) obj);
      case FUNCTION -> gm.registerFunction((FunctionDslObject) obj);
    }
  }
}
