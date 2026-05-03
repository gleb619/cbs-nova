package cbs.dsl.codegen;

import cbs.dsl.api.DslDefinition;
import cbs.dsl.api.DslDefinitionCollector;

import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles JEP 512 implicit-class DSL files and collects their {@link DslDefinition} instances.
 *
 * <p>Usage: {@code DslCompiler <source-dir> <output-dir>}
 *
 * <p>For each {@code *.java} source under {@code source-dir} the compiler:
 *
 * <ol>
 *   <li>Compiles with {@code --source 25} and the current {@code java.class.path}
 *   <li>Clears the {@link DslDefinitionCollector}
 *   <li>Loads the compiled implicit class and invokes its {@code main(String[])} method
 *   <li>Drains the collector to retrieve all definitions produced by the file
 *   <li>Validates each definition and prints a summary line
 * </ol>
 */
public class DslCompiler {

  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("Usage: DslCompiler <source-dir> <output-dir>");
      System.exit(1);
    }

    Path sourceDir = Path.of(args[0]);
    Path outputDir = Path.of(args[1]);

    if (!Files.isDirectory(sourceDir)) {
      System.err.println("Source directory does not exist: " + sourceDir);
      System.exit(1);
    }

    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      System.err.println("Failed to create output directory: " + outputDir);
      e.printStackTrace();
      System.exit(1);
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      System.err.println("No Java compiler available. Ensure a JDK (not JRE) is used.");
      System.exit(1);
    }

    List<File> sourceFiles = new ArrayList<>();
    try (var stream = Files.walk(sourceDir)) {
      stream
          .filter(p -> p.toString().endsWith(".java"))
          .map(Path::toFile)
          .forEach(sourceFiles::add);
    } catch (IOException e) {
      System.err.println("Failed to list source files: " + e.getMessage());
      System.exit(1);
    }

    if (sourceFiles.isEmpty()) {
      System.out.println("No Java source files found in " + sourceDir);
      System.exit(0);
    }

    Path wrapDir;
    List<File> wrappedFiles;
    try {
      wrapDir = Files.createTempDirectory("dsl-wrap");
      wrappedFiles = new ArrayList<>();
      for (File f : sourceFiles) {
        String content = Files.readString(f.toPath());
        String className = f.getName().replace(".java", "");
        if (!containsExplicitTypeDeclaration(content)) {
          Path wrapped = wrapDir.resolve(f.getName());
          String[] parts = splitImportsAndBody(content);
          String importBlock = parts[0].trim();
          String body = indent(parts[1].trim(), 4);
          StringBuilder sb = new StringBuilder();
          if (!importBlock.isEmpty()) {
            sb.append(importBlock).append("\n\n");
          }
          sb.append("public class ").append(className).append(" {\n")
              .append("  public static void main(String[] args) throws Exception {\n")
              .append(body).append("\n")
              .append("  }\n")
              .append("}\n");
          Files.writeString(wrapped, sb.toString());
          wrappedFiles.add(wrapped.toFile());
        } else {
          wrappedFiles.add(f);
        }
      }
    } catch (IOException e) {
      System.err.println("Failed to wrap DSL source files: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
      return;
    }

    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
    try {
      fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));
    } catch (IOException e) {
      System.err.println("Failed to set class output location: " + e.getMessage());
      System.exit(1);
    }

    List<String> options = new ArrayList<>();
    options.add("--source");
    options.add("25");
    String classPath = System.getProperty("java.class.path");
    if (classPath != null && !classPath.isEmpty()) {
      options.add("-classpath");
      options.add(classPath);
    }

    Iterable<? extends JavaFileObject> compilationUnits =
        fileManager.getJavaFileObjectsFromFiles(wrappedFiles);
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    JavaCompiler.CompilationTask task =
        compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

    boolean success = task.call();
    if (!success) {
      for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
        System.err.format(
            "%s:%d: %s: %s%n",
            d.getSource() != null ? d.getSource().getName() : "-",
            d.getLineNumber(),
            d.getKind(),
            d.getMessage(null));
      }
      System.exit(1);
    }

    try {
      deleteRecursively(wrapDir);
    } catch (IOException ignored) {
    }

    try {
      URLClassLoader classLoader = new URLClassLoader(
          new URL[] {outputDir.toUri().toURL()}, DslCompiler.class.getClassLoader());
      boolean validationFailed = false;

      for (File f : sourceFiles) {
        String fileName = f.getName();
        String className = fileName.substring(0, fileName.lastIndexOf('.'));

        DslDefinitionCollector.clear();
        invokeMain(classLoader, className);

        List<DslDefinition> definitions = DslDefinitionCollector.drain();
        for (DslDefinition def : definitions) {
          System.out.println("Compiled and validated: "
              + def.getCode()
              + " ("
              + def.getClass().getInterfaces()[0].getSimpleName()
              + ")");
        }
        if (definitions.isEmpty()) {
          System.err.println("No DslDefinition collected from " + className);
          validationFailed = true;
        }
      }

      if (validationFailed) {
        System.exit(1);
      }
    } catch (Exception e) {
      System.err.println("Validation failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }

    try {
      fileManager.close();
    } catch (IOException e) {
      // ignore
    }

    System.out.println("DSL compilation completed successfully. Output: " + outputDir);
  }

  private static void invokeMain(ClassLoader classLoader, String className) throws Exception {
    Class<?> clazz = classLoader.loadClass(className);
    Method mainMethod = clazz.getDeclaredMethod("main", String[].class);
    mainMethod.invoke(null, (Object) new String[0]);
  }

  private static boolean containsExplicitTypeDeclaration(String source) {
    return source.matches("(?s).*\\b(class|interface|enum|record)\\s+\\w+.*");
  }

  private static String[] splitImportsAndBody(String source) {
    StringBuilder imports = new StringBuilder();
    StringBuilder body = new StringBuilder();
    boolean inImports = true;
    for (String line : source.lines().toList()) {
      if (inImports && line.trim().startsWith("import ")) {
        imports.append(line).append("\n");
      } else {
        if (line.trim().isEmpty() && inImports && imports.length() > 0) {
          imports.append("\n");
        } else {
          inImports = false;
          body.append(line).append("\n");
        }
      }
    }
    return new String[] {imports.toString(), body.toString()};
  }

  private static String indent(String text, int spaces) {
    String prefix = " ".repeat(spaces);
    return text.lines()
        .map(line -> line.isBlank() ? line : prefix + line)
        .collect(Collectors.joining("\n"));
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (var entries = Files.list(path)) {
        entries.forEach(p -> {
          try {
            deleteRecursively(p);
          } catch (IOException ignored) {
          }
        });
      }
    }
    Files.deleteIfExists(path);
  }
}
