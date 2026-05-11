package cbs.dsl.codegen;

import cbs.dsl.api.DslComponent;
import cbs.dsl.api.DslObject;
import cbs.dsl.builder.EventDslObject;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import javax.tools.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/** Compiles JEP 512 implicit-class DSL files and collects their {@link DslObject} instances. */

// TODO: replace javaparser with `https://github.com/INRIA/spoon`
// implementation("fr.inria.gforge.spoon:spoon-core:10.3.0")
public class DslCompiler {

  private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();
  static final String WRAPPER_TEMPLATE = // language=java
      """
      import java.util.List;
      import cbs.dsl.api.DslCompilationUnit;
      import cbs.dsl.api.DslObject;

      {{imports}}public class {{className}} implements DslCompilationUnit {
          @Override
          public List<DslObject> define() {
      {{body}}
          }
      }
      """;
  private static final int PARALLEL_THREADS =
      Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  private static final Map<String, ParsedDsl> PARSED_DSL_MAP = new HashMap<>();

  public static void main(String[] args) {
    if (args.length != 2) {
      logError("Usage: DslCompiler <source-dir> <output-dir>%n");
      System.exit(1);
    }

    Path sourceDir = Path.of(args[0]);
    Path outputDir = Path.of(args[1]);

    validateDirectories(sourceDir);
    ensureOutputDirectoryExists(outputDir);
    ensureCompilerAvailable();

    List<File> sourceFiles = collectSourceFiles(sourceDir, outputDir);
    if (sourceFiles.isEmpty()) {
      logInfo("No Java source files found in " + sourceDir);
      System.exit(0);
    }

    try {
      List<File> wrappedFiles = wrapImplicitClasses(sourceFiles);
      compileWrappedFiles(wrappedFiles, outputDir);
      validateAndGenerate(sourceFiles, outputDir);
    } catch (Exception e) {
      logError("Compilation failed: %s%n", e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }

    logInfo("DSL compilation completed successfully. Output: %s%n", outputDir);
  }

  private static void validateDirectories(Path sourceDir) {
    if (!Files.isDirectory(sourceDir)) {
      logError("Source directory does not exist: %s%n", sourceDir);
      System.exit(1);
    }
  }

  private static void ensureOutputDirectoryExists(Path outputDir) {
    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      logError("Failed to create output directory: %s%n", outputDir);
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void ensureCompilerAvailable() {
    if (COMPILER == null) {
      logError("No Java compiler available. Ensure a JDK (not JRE) is used.%n");
      System.exit(1);
    }
  }

  private static List<File> collectSourceFiles(Path sourceDir, Path outputDir) {
    List<File> sourceFiles = new ArrayList<>();
    try (var stream = Files.walk(sourceDir)) {
      stream
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> !p.toAbsolutePath()
              .normalize()
              .startsWith(outputDir.toAbsolutePath().normalize()))
          .map(Path::toFile)
          .forEach(sourceFiles::add);
    } catch (IOException e) {
      logError("Failed to list source files: %s%n", e.getMessage());
      System.exit(1);
    }
    return sourceFiles;
  }

  private static List<File> wrapImplicitClasses(List<File> sourceFiles) throws IOException {
    Path wrapDir = Files.createTempDirectory("dsl-wrap");
    List<File> wrappedFiles = new ArrayList<>();

    for (File sourceFile : sourceFiles) {
      File wrappedFile = wrapIfImplicitClass(wrapDir, sourceFile);
      wrappedFiles.add(wrappedFile);
    }

    return wrappedFiles;
  }

  private static File wrapIfImplicitClass(Path wrapDir, File sourceFile) throws IOException {
    String content = Files.readString(sourceFile.toPath());
    String className = sourceFile.getName().replace(".java", "");

    if (!containsExplicitTypeDeclaration(content)) {
      Path wrappedPath = wrapDir.resolve(sourceFile.getName());
      ParsedDsl parsed = parseCompactDsl(content);
      PARSED_DSL_MAP.put(className, parsed);
      String importsBlock =
          parsed.imports() != null && !parsed.imports().isEmpty() ? parsed.imports() + "\n" : "";
      String wrappedContent = Substitutor.format(
          WRAPPER_TEMPLATE,
          Map.of("className", className, "body", parsed.body(), "imports", importsBlock));
      Files.writeString(wrappedPath, wrappedContent);
      return wrappedPath.toFile();
    } else {
      return sourceFile;
    }
  }

  public record ParsedDsl(String imports, String body) {}

  public static ParsedDsl parseCompactDsl(String content) {
    // Step 1: split imports and body with a lightweight line scan so imports stay
    // at compilation-unit level when we wrap the body in a temporary class.
    StringBuilder imports = new StringBuilder();
    StringBuilder body = new StringBuilder();
    boolean inImports = true;
    for (String line : content.lines().toList()) {
      if (inImports && line.trim().startsWith("import ")) {
        imports.append(line).append("\n");
      } else {
        if (line.trim().isEmpty() && inImports && !imports.isEmpty()) {
          imports.append("\n");
        } else {
          inImports = false;
          body.append(line).append("\n");
        }
      }
    }

    // Step 2: wrap body in a temp class (body already contains the define() method).
    String tempWrapper =
        """
        %sclass __Temp__ {
        %s
        }""".formatted(imports.isEmpty() ? "" : imports.toString().trim() + "\n\n", body);

    ParserConfiguration config = new ParserConfiguration();
    config.setAttributeComments(false);
    JavaParser parser = new JavaParser(config);
    ParseResult<CompilationUnit> result = parser.parse(tempWrapper);
    if (!result.isSuccessful()) {
      throw new IllegalStateException("Failed to parse wrapped DSL: " + result.getProblems());
    }
    CompilationUnit cu = result.getResult().orElseThrow();

    // Step 3: extract clean imports from the AST (comments already stripped).
    StringBuilder importBlock = new StringBuilder();
    cu.getImports().forEach(imp -> importBlock.append(imp.toString()).append("\n"));

    // Step 4: find the define() method (no parameters) and extract its body.
    MethodDeclaration defineMethod = cu.findFirst(
            MethodDeclaration.class,
            m -> "define".equals(m.getName().asString()) && m.getParameters().isEmpty())
        .orElseThrow(
            () -> new IllegalStateException("Could not locate define() method in compact DSL"));

    StringBuilder cleanBody = new StringBuilder();
    defineMethod.getBody().ifPresent(blockStmt -> blockStmt
        .getStatements()
        .forEach(stmt -> cleanBody.append(stmt.toString()).append("\n")));

    return new ParsedDsl(importBlock.toString().trim(), cleanBody.toString().trim());
  }

  private static void compileWrappedFiles(List<File> files, Path outputDir) {
    int batchSize = Math.max(1, files.size() / PARALLEL_THREADS);
    List<List<File>> batches = new ArrayList<>();
    for (int i = 0; i < files.size(); i += batchSize) {
      batches.add(files.subList(i, Math.min(i + batchSize, files.size())));
    }

    List<CompletableFuture<List<Diagnostic<? extends JavaFileObject>>>> futures = new ArrayList<>();
    for (List<File> batch : batches) {
      CompletableFuture<List<Diagnostic<? extends JavaFileObject>>> future =
          CompletableFuture.supplyAsync(() -> compileBatch(batch, outputDir));
      futures.add(future);
    }

    List<Diagnostic<? extends JavaFileObject>> allDiagnostics = new ArrayList<>();
    boolean compilationFailed = false;
    for (CompletableFuture<List<Diagnostic<? extends JavaFileObject>>> future : futures) {
      List<Diagnostic<? extends JavaFileObject>> diagnostics = future.join();
      allDiagnostics.addAll(diagnostics);
      if (!diagnostics.isEmpty()) {
        compilationFailed = true;
      }
    }

    if (compilationFailed) {
      reportCompilationErrors(allDiagnostics);
      logError("Compilation errors found — see above.%n");
      System.exit(1);
    }
  }

  private static List<Diagnostic<? extends JavaFileObject>> compileBatch(
      List<File> batch, Path outputDir) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager =
        COMPILER.getStandardFileManager(diagnostics, null, null)) {
      fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(batch);
      List<String> options = getCompilationOptions();
      JavaCompiler.CompilationTask task =
          COMPILER.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
      boolean failed = !task.call();
      return failed ? new ArrayList<>(diagnostics.getDiagnostics()) : List.of();
    } catch (IOException e) {
      logError("Failed to configure file manager: %s%n", e.getMessage());
      e.printStackTrace();
      System.exit(1);
      return List.of();
    }
  }

  private static List<String> getCompilationOptions() {
    List<String> options = new ArrayList<>();
    options.add("-implicit:class");
    String classPath = System.getProperty("java.class.path");
    if (classPath != null && !classPath.isEmpty()) {
      options.add("-classpath");
      options.add(classPath);
    }
    return options;
  }

  private static void reportCompilationErrors(
      List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    for (Diagnostic<? extends JavaFileObject> d : diagnostics) {
      logError(
          "%s:%d: %s: %s%n",
          d.getSource() != null ? d.getSource().getName() : "-",
          d.getLineNumber(),
          d.getKind(),
          d.getMessage(null));
    }
  }

  private static void validateAndGenerate(List<File> sourceFiles, Path outputDir) throws Exception {
    Path tempWrapDir = resolveTempWrapDir();
    withClassLoader(outputDir, classLoader -> {
      ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
      try {
        List<CompletableFuture<GenerationResult>> futures = new ArrayList<>();
        for (File sourceFile : sourceFiles) {
          CompletableFuture<GenerationResult> future = CompletableFuture.supplyAsync(
              () -> generateInVirtualThread(classLoader, sourceFile, outputDir), virtualExecutor);
          futures.add(future);
        }

        List<FileWrite> allFiles = new ArrayList<>();
        boolean hasError = false;
        for (CompletableFuture<GenerationResult> future : futures) {
          GenerationResult result = future.join();
          if (result.hasError()) {
            hasError = true;
          }
          allFiles.addAll(result.files());
        }

        if (hasError) {
          System.exit(1);
        }

        for (FileWrite fw : allFiles) {
          try {
            fw.writeToDisk();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      } finally {
        virtualExecutor.shutdown();
      }
    });
    cleanupTempWrapDir(tempWrapDir);
  }

  record FileWrite(Path path, String content) {
    void writeToDisk() throws IOException {
      Files.createDirectories(path.getParent());
      Files.writeString(path, content);
    }
  }

  record GenerationResult(List<FileWrite> files, boolean hasError) {}

  private static GenerationResult generateInVirtualThread(
      URLClassLoader classLoader, File sourceFile, Path outputDir) {
    List<FileWrite> files = new ArrayList<>();
    boolean hasError = false;
    try {
      files = generateFiles(classLoader, sourceFile, outputDir);
    } catch (Exception e) {
      logError("Failed to generate code for %s: %s%n", sourceFile.getName(), e.getMessage());
      hasError = true;
    }
    return new GenerationResult(files, hasError);
  }

  private static Path resolveTempWrapDir() {
    try {
      return Files.createTempDirectory("dsl-wrap");
    } catch (IOException e) {
      logError("Failed to create temporary directory: %s%n", e.getMessage());
      System.exit(1);
      return null;
    }
  }

  private static void cleanupTempWrapDir(Path tempWrapDir) {
    if (tempWrapDir != null) {
      try {
        deleteRecursively(tempWrapDir);
      } catch (IOException ignored) {
      }
    }
  }

  private static void withClassLoader(Path outputDir, Consumer<URLClassLoader> action)
      throws Exception {
    try (URLClassLoader classLoader = new URLClassLoader(
        new URL[] {outputDir.toUri().toURL()}, DslCompiler.class.getClassLoader())) {
      action.accept(classLoader);
    }
  }

  private static List<FileWrite> generateFiles(
      URLClassLoader classLoader, File sourceFile, Path outputDir) throws Exception {
    String className = extractClassName(sourceFile);

    List<DslObject> objects;
    try {
      objects = getDslObjects(classLoader, className);
    } catch (Exception e) {
      logError("Failed to load DSL objects for %s: %s%n", className, e.getMessage());
      throw e;
    }

    if (objects.isEmpty()) {
      logError("No DslObject collected from %s%n", className);
      throw new IllegalStateException("No DslObject collected from " + className);
    }

    List<FileWrite> generatedFiles = new ArrayList<>();
    ParsedDsl parsed = PARSED_DSL_MAP.get(className);
    for (DslObject obj : objects) {
      logInfo(
          "Compiled and validated: %s (%s)",
          obj.code(),
          obj.getClass().getEnclosingClass() != null
              ? obj.getClass().getEnclosingClass().getSimpleName()
              : obj.getClass().getSimpleName());
      RegistrationModel spec = toRegistrationSpec(obj, className, parsed);
      Function<RegistrationModel, String> dslBodyProvider =
          s -> parsed != null ? parsed.body() : "UndefinedDslObject.create();";
      switch (spec.interfaceType()) {
        case EVENT -> {
          EventSpecificationGenerator wfGen = new EventSpecificationGenerator();
          List<String> txCodes = new ArrayList<>();
          //List<String> txCodes = ((EventDslObject) obj).transactionCodes();
          String wfImplClassName = "cbs.dsl.codegen.generated.%sEventWorkflowImpl"
              .formatted(CodeGenUtil.toClassName(obj.code()));
          EventSpecificationModel wfSpec =
              new EventSpecificationModel(obj.code(), className, txCodes, spec.dslBody(), spec.dslImports(), wfImplClassName);
          generatedFiles.addAll(wfGen.generateFileSpecs(List.of(wfSpec), outputDir));
          EventDefinitionGenerator gen = new EventDefinitionGenerator(dslBodyProvider);
          generatedFiles.addAll(gen.generateFileSpecs(spec, outputDir));
        }
        case TRANSACTION -> {
          TransactionDefinitionGenerator gen = new TransactionDefinitionGenerator(dslBodyProvider);
          generatedFiles.addAll(gen.generateFileSpecs(spec, outputDir));
        }
        case HELPER -> {
          HelperDefinitionGenerator gen = new HelperDefinitionGenerator(dslBodyProvider);
          generatedFiles.addAll(gen.generateFileSpecs(spec, outputDir));
        }
        case WORKFLOW -> {
          WorkflowDefinitionGenerator gen = new WorkflowDefinitionGenerator(dslBodyProvider);
          generatedFiles.addAll(gen.generateFileSpecs(spec, outputDir));
        }
        case CONDITION -> {
          ConditionDefinitionGenerator gen = new ConditionDefinitionGenerator(dslBodyProvider);
          generatedFiles.addAll(gen.generateFileSpecs(spec, outputDir));
        }
        case MASS_OPERATION -> {
          MassOperationDefinitionGenerator gen =
              new MassOperationDefinitionGenerator(dslBodyProvider);
          generatedFiles.addAll(gen.generateFileSpecs(spec, outputDir));
        }
        default ->
          throw new IllegalStateException("Unknown interface type: " + spec.interfaceType());
      }
      logInfo("Generated code for: %s%n", obj.code());
    }

    return generatedFiles;
  }

  private static RegistrationModel toRegistrationSpec(
      DslObject obj, String className, ParsedDsl parsed) {
    DslInterfaceType interfaceType = resolveInterfaceType(obj);
    String inputType = resolveInputType(interfaceType);
    String outputType = resolveOutputType(interfaceType);
    boolean isDsl = parsed != null;
    String uniqueClassName = (isDsl && interfaceType == DslInterfaceType.HELPER)
        ? className + "_" + CodeGenUtil.toClassName(obj.code())
        : className;
    return new RegistrationModel(
        "",
        uniqueClassName,
        obj.code(),
        interfaceType,
        inputType,
        outputType,
        DslComponent.DslComponentModel.SIMPLE,
        parsed != null ? parsed.body() : null,
        parsed != null ? parsed.imports() : null,
        null,
        isDsl,
        className);
  }

  private static DslInterfaceType resolveInterfaceType(DslObject obj) {
    Class<?> clazz = obj.getClass();
    String simpleName = clazz.getSimpleName();
    return switch (simpleName) {
      case "EventDslObject" -> DslInterfaceType.EVENT;
      case "TransactionDslObject" -> DslInterfaceType.TRANSACTION;
      case "HelperDslObject" -> DslInterfaceType.HELPER;
      case "WorkflowDslObject" -> DslInterfaceType.WORKFLOW;
      case "ConditionDslObject" -> DslInterfaceType.CONDITION;
      case "MassOperationDslObject" -> DslInterfaceType.MASS_OPERATION;
      default -> {
        Class<?> enclosing = clazz.getEnclosingClass();
        yield switch (enclosing != null ? enclosing.getSimpleName() : "") {
          case "EventBuilder" -> DslInterfaceType.EVENT;
          case "TransactionBuilder" -> DslInterfaceType.TRANSACTION;
          case "HelperBuilder" -> DslInterfaceType.HELPER;
          case "WorkflowBuilder" -> DslInterfaceType.WORKFLOW;
          case "ConditionBuilder" -> DslInterfaceType.CONDITION;
          case "MassOperationBuilder" -> DslInterfaceType.MASS_OPERATION;
          default ->
            throw new IllegalArgumentException("Unsupported DslObject type: " + simpleName);
        };
      }
    };
  }

  private static String resolveInputType(DslInterfaceType type) {
    return switch (type) {
      case EVENT -> "cbs.dsl.api.EventTypes.EventInput";
      case TRANSACTION -> "cbs.dsl.api.TransactionTypes.TransactionInput";
      case HELPER -> "cbs.dsl.api.HelperTypes.HelperInput";
      case WORKFLOW -> "cbs.dsl.api.WorkflowTypes.WorkflowInput";
      case CONDITION -> "cbs.dsl.api.ConditionTypes.ConditionInput";
      case MASS_OPERATION -> "cbs.dsl.api.MassOperationTypes.MassOperationInput";
    };
  }

  private static String resolveOutputType(DslInterfaceType type) {
    return switch (type) {
      case EVENT -> "cbs.dsl.api.EventTypes.EventOutput";
      case TRANSACTION -> "cbs.dsl.api.TransactionTypes.TransactionOutput";
      case HELPER -> "cbs.dsl.api.HelperTypes.HelperOutput";
      case WORKFLOW -> "cbs.dsl.api.WorkflowTypes.WorkflowOutput";
      case CONDITION -> "cbs.dsl.api.ConditionTypes.ConditionOutput";
      case MASS_OPERATION -> "cbs.dsl.api.MassOperationTypes.MassOperationOutput";
    };
  }

  private static String extractClassName(File sourceFile) {
    String fileName = sourceFile.getName();
    return fileName.substring(0, fileName.lastIndexOf('.'));
  }

  @SuppressWarnings("unchecked")
  private static List<DslObject> getDslObjects(URLClassLoader classLoader, String className)
      throws Exception {
    Class<?> clazz = classLoader.loadClass(className);
    Object instance = clazz.getDeclaredConstructor().newInstance();
    Method getter = clazz.getMethod("define");
    return (List<DslObject>) getter.invoke(instance);
  }

  public static boolean containsExplicitTypeDeclaration(String source) {
    // Use regex directly — JavaParser 3.28+ parses JEP 512 implicit classes
    // as valid compilation units with types, which breaks the old check.
    return source.matches("(?s).*\\b(class|interface|enum|record)\\s+\\w+.*");
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (var entries = Files.list(path)) {
        for (Path entry : (Iterable<Path>) entries::iterator) {
          deleteRecursively(entry);
        }
      }
    }

    Files.deleteIfExists(path);
  }

  private static void logInfo(String message, Object... args) {
    System.out.printf(message, args);
  }

  private static void logError(String message, Object... args) {
    System.err.printf(message, args);
  }
}
