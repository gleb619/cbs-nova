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

public class ConditionDefinitionGenerator implements DefinitionGenerator {

  private static final String CN_INPUT = "cbs.dsl.api.ConditionTypes.ConditionInput";
  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;
  private final Path outputDir;
  private final Function<RegistrationModel, String> dslBodyProvider;

  public ConditionDefinitionGenerator(Function<RegistrationModel, String> dslBodyProvider) {
    this(null, null, dslBodyProvider);
  }

  public ConditionDefinitionGenerator(
      Path outputDir, Function<RegistrationModel, String> dslBodyProvider) {
    this(null, outputDir, dslBodyProvider);
  }

  public ConditionDefinitionGenerator(Filer filer, Function<RegistrationModel, String> dslBodyProvider) {
    this(filer, null, dslBodyProvider);
  }

  private ConditionDefinitionGenerator(
      Filer filer, Path outputDir, Function<RegistrationModel, String> dslBodyProvider) {
    this.filer = filer;
    this.outputDir = outputDir;
    this.dslBodyProvider = dslBodyProvider;
  }

  @Override
  public List<FileWrite> generate(List<RegistrationModel> specs) throws IOException {
    if (outputDir != null) {
      List<FileWrite> files = new ArrayList<>();
      for (RegistrationModel spec : specs) {
        files.addAll(generateFileSpecs(spec, outputDir));
      }
      return files;
    }
    if (filer != null) {
      for (RegistrationModel spec : specs) {
        String activitySource = generateActivityInterfaceCode(spec);
        writeActivityInterface(spec, activitySource);
        String definitionSource = generateDefinitionCode(spec);
        writeDefinition(spec, definitionSource);
      }
    }
    return List.of();
  }

  @Override
  public void write(List<FileWrite> files) throws IOException {
    for (FileWrite fw : files) {
      Files.createDirectories(fw.path().getParent());
      Files.writeString(fw.path(), fw.content());
    }
  }

  public String generateActivityInterfaceCode(RegistrationModel spec) {
    String className = spec.className() + "ConditionActivity";
    String timestamp = CodeGenUtil.currentTimestamp();

    return Substitutor.format(
        // language=java
        """
        package {{package}};

        import cbs.dsl.api.ConditionTypes.ConditionInput;
        import cbs.dsl.api.ConditionTypes.ConditionOutput;
        import io.temporal.activity.ActivityInterface;
        import io.temporal.activity.ActivityMethod;
        import javax.annotation.processing.Generated;

        @Generated(
            value = "cbs.dsl.codegen.ConditionDefinitionGenerator",
            date = "{{timestamp}}"
        )
        @ActivityInterface
        public interface {{className}} {

            @ActivityMethod
            ConditionOutput evaluate(ConditionInput input);
        }
        """,
        Map.of(
            "package", GENERATED_PACKAGE,
            "timestamp", timestamp,
            "className", className));
  }

  public void writeActivityInterface(RegistrationModel spec, String source) throws IOException {
    String className = spec.className() + "ConditionActivity";
    String fqcn = GENERATED_PACKAGE + "." + className;
    CodeGenUtil.writeToFiler(filer, fqcn, source);
  }

  public String generateDefinitionCode(RegistrationModel spec) {
    String wrapperClassName = spec.className() + "Definition";
    String activityInterfaceName = spec.className() + "ConditionActivity";

    String timestamp = CodeGenUtil.currentTimestamp();
    boolean inputIsRuntime = spec.inputType().equals(CN_INPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputType}}.class)",
            Map.of("inputType", CodeGenUtil.simpleName(spec.inputType())));

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime
        ? ""
        : Substitutor.format("import {{inputType}};\n", Map.of("inputType", spec.inputType()));
    String parameterScannerImports = inputIsRuntime
        ? ""
        : "import cbs.dsl.api.ParameterScanner;\n        import cbs.dsl.api.ParameterScanner.ParameterScanResult;\n        ";

    String parametersField = inputIsRuntime
        ? ""
        : "private static final ParameterScanResult PARAMETERS = ParameterScanner.scan(%s.class);\n\n".formatted(
            CodeGenUtil.simpleName(spec.inputType()));

    String getParametersOverride = inputIsRuntime
        ? ""
        : """
          @Override
          public List<ParameterDefinition> getParameters() {
              return PARAMETERS.definitions();
          }
          """;

    String dslBody = dslBodyProvider.apply(spec);
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : "import cbs.dsl.builder.UndefinedDslObject;\n";

    return Substitutor.format(
        // language=java
        """
        package {{DEFINITIONS_PACKAGE}};

        import cbs.dsl.api.ConditionDefinition;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.ParameterDefinition;
        {{parameterScannerImports}}import cbs.dsl.api.ConditionTypes.ConditionInput;
        import cbs.dsl.api.ConditionTypes.ConditionOutput;
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.context.TransactionContext;
        import {{generatedPackage}}.{{activityInterfaceName}};
        {{jsonPayloadImport}}        import {{specPackage}}.{{specClass}};
        {{inputTypeImport}}        import {{outputType}};
        {{dslImportsBlock}}        import java.util.Collections;
        import java.util.List;
        import java.util.function.Predicate;
        import javax.annotation.processing.Generated;

        /**
         * Generated ConditionDefinition wrapper for {{specClass}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.ConditionDefinitionGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClass}} implements ConditionDefinition, {{activityInterfaceName}} {

            private final {{specClass}} function;

            {{parametersField}}

            public {{wrapperClass}}(DslComponentResolver resolver) {
                this.function = resolver.resolve({{specClass}}.class);
            }

            @Override
            public String getCode() {
                return "{{code}}";
            }
            
            {{getParametersOverride}}
            @Override
            public ContextOutput prepare(Map<String, Object> params) {
                return prepareContext(params);
            }
            
            @Override
            public Predicate<TransactionContext> getPredicate() {
                return ctx -> false;
            }

            @Override
            public ConditionOutput evaluate(ConditionInput input) {
                {{inputTypeName}} typed = {{inputConversion}};
                {{outputTypeName}} out = function.evaluate(typed);
                return new ConditionOutput(out.getValue());
            }

            @Override
            public ConditionOutput check(ConditionInput input) {
                return evaluate(input);
            }

            @Override
            public DslObject dsl() {
                return {{dslBody}}
            }
        }
        """,
        Map.ofEntries(
            Map.entry("DEFINITIONS_PACKAGE", DEFINITIONS_PACKAGE),
            Map.entry("generatedPackage", GENERATED_PACKAGE),
            Map.entry("activityInterfaceName", activityInterfaceName),
            Map.entry("jsonPayloadImport", jsonPayloadImport),
            Map.entry("specPackage", spec.packageName()),
            Map.entry("specClass", spec.className()),
            Map.entry("inputTypeImport", inputTypeImport),
            Map.entry("outputType", spec.outputType()),
            Map.entry("parameterScannerImports", parameterScannerImports),
            Map.entry("parametersField", parametersField),
            Map.entry("getParametersOverride", getParametersOverride),
            Map.entry("dslImportsBlock", dslImportsBlock),
            Map.entry("timestamp", timestamp),
            Map.entry("wrapperClass", wrapperClassName),
            Map.entry("code", spec.code()),
            Map.entry("inputTypeName", CodeGenUtil.simpleName(spec.inputType())),
            Map.entry("inputConversion", inputConversion),
            Map.entry("outputTypeName", CodeGenUtil.simpleName(spec.outputType())),
            Map.entry("dslBody", dslBody)));
  }

  public List<FileWrite> generateFileSpecs(RegistrationModel spec, Path outputDir) {
    List<FileWrite> files = new ArrayList<>();
    String activitySource = generateActivityInterfaceCode(spec);
    files.add(writeActivityInterfaceToSpec(spec, activitySource, outputDir));
    String definitionSource = generateDefinitionCode(spec);
    files.add(writeDefinitionToSpec(spec, definitionSource, outputDir));
    return files;
  }

  private FileWrite writeActivityInterfaceToSpec(
      RegistrationModel spec, String source, Path outputDir) {
    String className = spec.className() + "ConditionActivity";
    Path outputPath = outputDir.resolve("cbs/dsl/codegen/generated").resolve(className + ".java");
    return new FileWrite(outputPath, source);
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

  public void writeActivityInterfaceToPath(RegistrationModel spec, String source, Path outputDir)
      throws IOException {
    String className = spec.className() + "ConditionActivity";
    Path outputPath = outputDir.resolve("cbs/dsl/codegen/generated").resolve(className + ".java");
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, source);
  }
}
