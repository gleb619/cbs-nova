package cbs.dsl.codegen;

import cbs.dsl.codegen.DslCompiler.FileWrite;

import javax.annotation.processing.Filer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HelperDefinitionGenerator implements DefinitionGenerator {

  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;
  private final Path outputDir;
  private final Function<RegistrationModel, String> dslBodyProvider;

  public HelperDefinitionGenerator(Function<RegistrationModel, String> dslBodyProvider) {
    this(null, null, dslBodyProvider);
  }

  public HelperDefinitionGenerator(
      Filer filer, Function<RegistrationModel, String> dslBodyProvider) {
    this(filer, null, dslBodyProvider);
  }

  public HelperDefinitionGenerator(
      Path outputDir, Function<RegistrationModel, String> dslBodyProvider) {
    this(null, outputDir, dslBodyProvider);
  }

  private HelperDefinitionGenerator(
      Filer filer, Path outputDir, Function<RegistrationModel, String> dslBodyProvider) {
    this.filer = filer;
    this.outputDir = outputDir;
    this.dslBodyProvider = dslBodyProvider;
  }

  @Override
  public List<FileWrite> generate(List<RegistrationModel> specs) throws IOException {
    Map<String, List<RegistrationModel>> dslGroups = groupDslSpecs(specs);

    List<FileWrite> files = new ArrayList<>();

    if (outputDir != null) {
      for (Map.Entry<String, List<RegistrationModel>> entry : dslGroups.entrySet()) {
        files.add(generateSharedDslFile(entry.getKey(), entry.getValue().get(0)));
      }
      for (RegistrationModel spec : specs) {
        files.addAll(generateFileSpecs(spec, outputDir, dslGroups));
      }
      return files;
    }

    if (filer != null) {
      for (Map.Entry<String, List<RegistrationModel>> entry : dslGroups.entrySet()) {
        writeSharedDslToFiler(entry.getKey(), entry.getValue().get(0));
      }
      for (RegistrationModel spec : specs) {
        writeDefinition(spec, generateDefinitionCode(spec, dslGroups));
      }
    }

    return List.of();
  }

  private Map<String, List<RegistrationModel>> groupDslSpecs(List<RegistrationModel> specs) {
    return specs.stream()
        .filter(RegistrationModel::dslGenerated)
        .filter(s -> s.dslSourceClassName() != null && !s.dslSourceClassName().isBlank())
        .filter(s -> {
          String body = dslBodyProvider.apply(s);
          return body != null && !body.isBlank() && !body.contains("UndefinedDslObject");
        })
        .collect(Collectors.groupingBy(RegistrationModel::dslSourceClassName));
  }

  @Override
  public void write(List<FileWrite> files) throws IOException {
    for (FileWrite fw : files) {
      Files.createDirectories(fw.path().getParent());
      Files.writeString(fw.path(), fw.content());
    }
  }

  public String generateDefinitionCode(RegistrationModel spec) {
    return generateDefinitionCode(spec, Map.of());
  }

  public String generateDefinitionCode(
      RegistrationModel spec, Map<String, List<RegistrationModel>> dslGroups) {
    boolean hasSharedClass = spec.dslGenerated()
        && spec.dslSourceClassName() != null
        && dslGroups.containsKey(spec.dslSourceClassName());

    String wrapperClassName = spec.className() + "Definition";
    String timestamp = CodeGenUtil.currentTimestamp();

    boolean inputIsRuntime = spec.inputType().equals("cbs.dsl.api.HelperTypes.HelperInput");
    boolean outputIsRuntime = spec.outputType().equals("cbs.dsl.api.HelperTypes.HelperOutput");

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputType}}.class)",
            Map.of("inputType", CodeGenUtil.simpleName(spec.inputType())));

    String outputConversion = outputIsRuntime ? "out" : "new HelperOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime
        ? ""
        : Substitutor.format("import {{inputType}};\n", Map.of("inputType", spec.inputType()));
    String parameterScannerImports = inputIsRuntime
        ? ""
        : "import cbs.dsl.api.ParameterScanner;\nimport cbs.dsl.api.ParameterScanner.ParameterScanResult;\n";
    String outputTypeImport = outputIsRuntime
        ? ""
        : Substitutor.format("import {{outputType}};\n", Map.of("outputType", spec.outputType()));

    String parametersField = inputIsRuntime
        ? ""
        : "private static final ParameterScanResult PARAMETERS = ParameterScanner.scan(%s.class);\n\n    "
            .formatted(CodeGenUtil.simpleName(spec.inputType()));

    String getParametersOverride = inputIsRuntime ? "" : """
          @Override
          public List<ParameterDefinition> getParameters() {
              if (dsl() instanceof HelperDslObject dsl) {
                  return dsl.parameters();
              }
              return PARAMETERS.definitions();
          }
          """;

    String dslBody = buildDslBody(spec, hasSharedClass);

    String dslImportsBlock;
    if (!hasSharedClass
        && spec.dslImports() != null
        && !spec.dslImports().isBlank()) {
      dslImportsBlock = spec.dslImports().trim() + "\n";
    } else if (!hasSharedClass) {
      dslImportsBlock = "import cbs.dsl.builder.UndefinedDslObject;\n";
    } else {
      dslImportsBlock = "";
    }

    String specImport =
        (spec.packageName().isBlank() || spec.className().isBlank() || spec.dslGenerated())
            ? ""
            : "import " + spec.packageName() + "." + spec.className() + ";\n";

    String functionField =
        spec.dslGenerated() ? "" : "private final " + spec.className() + " function;\n    ";

    String functionAssignment = spec.dslGenerated()
        ? ""
        : "this.function = resolver.resolve(" + spec.className() + ".class);\n        ";

    String previewFallback = spec.dslGenerated()
        ? "throw new IllegalStateException(\"DSL object not available for preview\");"
        : "{{inputTypeName}} typed = {{inputConversion}};\n        {{outputTypeName}} out = function.preview(typed);\n        return {{outputConversion}};";

    String executeFallback = spec.dslGenerated()
        ? "throw new IllegalStateException(\"DSL object not available for execute\");"
        : "{{inputTypeName}} typed = {{inputConversion}};\n        {{outputTypeName}} out = function.execute(typed);\n        return {{outputConversion}};";

    Map<String, String> fallbackParams = Map.of(
        "inputTypeName", CodeGenUtil.simpleName(spec.inputType()),
        "inputConversion", inputConversion,
        "outputTypeName", CodeGenUtil.simpleName(spec.outputType()),
        "outputConversion", outputConversion);
    previewFallback = Substitutor.format(previewFallback, fallbackParams);
    executeFallback = Substitutor.format(executeFallback, fallbackParams);

    return Substitutor.format(
        // language=java
        """
        package {{DEFINITIONS_PACKAGE}};

        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.HelperDefinition;
        import cbs.dsl.api.ParameterDefinition;
        import cbs.dsl.api.HelperTypes.HelperInput;
        import cbs.dsl.api.HelperTypes.HelperOutput;
        import cbs.dsl.builder.HelperDslObject;
        import cbs.dsl.evaluator.Evaluator;
        {{jsonPayloadImport}}{{parameterScannerImports}}{{specImport}}{{inputTypeImport}}{{outputTypeImport}}{{dslImportsBlock}}import java.util.List;
        import javax.annotation.processing.Generated;

        /**
         * Generated HelperDefinition for {{specClass}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.HelperDefinitionGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClass}} implements HelperDefinition {

            {{functionField}}private final Evaluator evaluator;

            {{parametersField}}

            public {{wrapperClass}}(DslComponentResolver resolver) {
                {{functionAssignment}}this.evaluator = resolver.resolveEvaluator();
            }

            @Override
            public String getCode() {
                return "{{code}}";
            }

            {{getParametersOverride}}
            @Override
            public HelperOutput preview(HelperInput input) {
                if (dsl() instanceof HelperDslObject dsl) {
                    return evaluator.previewHelper(dsl, input);
                }
                {{previewFallback}}
            }

            @Override
            public HelperOutput execute(HelperInput input) {
                if (dsl() instanceof HelperDslObject dsl) {
                    return evaluator.executeHelper(dsl, input);
                }
                {{executeFallback}}
            }

            @Override
            public DslObject dsl() {
                {{dslBody}}
            }
        }
        """,
        Map.ofEntries(
            Map.entry("DEFINITIONS_PACKAGE", DEFINITIONS_PACKAGE),
            Map.entry("jsonPayloadImport", jsonPayloadImport),
            Map.entry("parameterScannerImports", parameterScannerImports),
            Map.entry("specImport", specImport),
            Map.entry("inputTypeImport", inputTypeImport),
            Map.entry("outputTypeImport", outputTypeImport),
            Map.entry("dslImportsBlock", dslImportsBlock),
            Map.entry("timestamp", timestamp),
            Map.entry("wrapperClass", wrapperClassName),
            Map.entry("specClass", spec.className()),
            Map.entry("code", spec.code()),
            Map.entry("functionField", functionField),
            Map.entry("functionAssignment", functionAssignment),
            Map.entry("parametersField", parametersField),
            Map.entry("getParametersOverride", getParametersOverride),
            Map.entry("previewFallback", previewFallback),
            Map.entry("executeFallback", executeFallback),
            Map.entry("inputTypeName", CodeGenUtil.simpleName(spec.inputType())),
            Map.entry("inputConversion", inputConversion),
            Map.entry("outputTypeName", CodeGenUtil.simpleName(spec.outputType())),
            Map.entry("outputConversion", outputConversion),
            Map.entry("dslBody", dslBody)));
  }

  private String buildDslBody(RegistrationModel spec, boolean hasSharedClass) {
    if (hasSharedClass) {
      String sharedClassName = spec.dslSourceClassName() + "Generated";
      return Substitutor.format(
          "{{sharedClassName}}.get(\"{{code}}\")",
          Map.of("sharedClassName", sharedClassName, "code", spec.code()));
    }
    String rawDslBody = dslBodyProvider.apply(spec);
    return rawDslBody != null ? rawDslBody : "UndefinedDslObject.create();";
  }

  private FileWrite generateSharedDslFile(
      String dslSourceClassName, RegistrationModel representative) {
    String className = dslSourceClassName + "Generated";
    String source = generateSharedDslClass(dslSourceClassName, representative);
    Path outputPath =
        outputDir.resolve("cbs/dsl/codegen/generated/definitions").resolve(className + ".java");
    return new FileWrite(outputPath, source);
  }

  private void writeSharedDslToFiler(String dslSourceClassName, RegistrationModel representative)
      throws IOException {
    String className = dslSourceClassName + "Generated";
    String source = generateSharedDslClass(dslSourceClassName, representative);
    CodeGenUtil.writeToFiler(filer, DEFINITIONS_PACKAGE + "." + className, source);
  }

  private String generateSharedDslClass(String dslSourceClassName, RegistrationModel spec) {
    String className = dslSourceClassName + "Generated";
    String dslBody = dslBodyProvider.apply(spec);
    String dslImports = spec.dslImports();

    String trimmed = dslBody.trim();
    if (trimmed.endsWith(";")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }

    String imports = dslImports != null && !dslImports.isBlank() ? dslImports.trim() + "\n" : "";

    return Substitutor.format(
        // language=java
        """
        package {{package}};

        import cbs.dsl.builder.HelperDslObject;
        {{imports}}import java.util.List;
        import javax.annotation.processing.Generated;

        /**
         * Generated helper container for {{sourceClass}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.HelperDefinitionGenerator",
            date = "{{timestamp}}"
        )
        public class {{className}} {
            public static final List<HelperDslObject> HELPERS = {{dslBody}};

            public static HelperDslObject get(String code) {
                return HELPERS.stream()
                    .filter(o -> code.equals(o.code()))
                    .findFirst()
                    .orElseThrow();
            }
        }
        """,
        Map.of(
            "package", DEFINITIONS_PACKAGE,
            "className", className,
            "sourceClass", dslSourceClassName,
            "imports", imports,
            "timestamp", CodeGenUtil.currentTimestamp(),
            "dslBody", trimmed));
  }

  public List<FileWrite> generateFileSpecs(RegistrationModel spec, Path outputDir) {
    return generateFileSpecs(spec, outputDir, Map.of());
  }

  public List<FileWrite> generateFileSpecs(
      RegistrationModel spec, Path outputDir, Map<String, List<RegistrationModel>> dslGroups) {
    List<FileWrite> files = new ArrayList<>();
    String definitionSource = generateDefinitionCode(spec, dslGroups);
    files.add(writeDefinitionToSpec(spec, definitionSource, outputDir));
    return files;
  }

  private FileWrite writeDefinitionToSpec(RegistrationModel spec, String source, Path outputDir) {
    String wrapperClassName = spec.className() + "Definition";
    Path outputPath = outputDir
        .resolve("cbs/dsl/codegen/generated/definitions")
        .resolve(wrapperClassName + ".java");
    return new FileWrite(outputPath, source);
  }

  public void writeDefinition(RegistrationModel spec, String source) throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    String qualifiedName = DEFINITIONS_PACKAGE + "." + wrapperClassName;
    CodeGenUtil.writeToFiler(filer, qualifiedName, source);
  }
}
