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

public class HelperDefinitionGenerator implements DefinitionGenerator {

  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;
  private final Path outputDir;
  private final Function<RegistrationModel, String> dslBodyProvider;

  public HelperDefinitionGenerator(Function<RegistrationModel, String> dslBodyProvider) {
    this(null, null, dslBodyProvider);
  }

  public HelperDefinitionGenerator(Filer filer, Function<RegistrationModel, String> dslBodyProvider) {
    this(filer, null, dslBodyProvider);
  }

  public HelperDefinitionGenerator(Path outputDir, Function<RegistrationModel, String> dslBodyProvider) {
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
    if (outputDir != null) {
      List<FileWrite> files = new ArrayList<>();
      for (RegistrationModel spec : specs) {
        files.addAll(generateFileSpecs(spec, outputDir));
      }
      return files;
    }
    if (filer != null) {
      for (RegistrationModel spec : specs) {
        writeDefinition(spec, generateDefinitionCode(spec));
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

  public String generateDefinitionCode(RegistrationModel spec) {
    String wrapperClassName = spec.className() + "Definition";

    String timestamp = CodeGenUtil.currentTimestamp();
    boolean inputIsRuntime = spec.inputType().equals("cbs.dsl.api.HelperTypes.HelperInput");
    boolean outputIsRuntime = spec.outputType().equals("cbs.dsl.api.HelperTypes.HelperOutput");

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputType}}.class)",
            Map.of("inputType", CodeGenUtil.simpleName(spec.inputType())));

    String outputConversion = outputIsRuntime ? "out" : "new HelperOutput(JsonPayload.params(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime
        ? ""
        : Substitutor.format("import {{inputType}};\n", Map.of("inputType", spec.inputType()));
    String parameterScannerImports = inputIsRuntime
        ? ""
        : "import cbs.dsl.api.ParameterScanner;\n        import cbs.dsl.api.ParameterScanner.ParameterScanResult;\n        ";
    String outputTypeImport = outputIsRuntime
        ? ""
        : Substitutor.format("import {{outputType}};\n", Map.of("outputType", spec.outputType()));

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
        
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.HelperDefinition;
        import cbs.dsl.api.ParameterDefinition;
        {{parameterScannerImports}}import cbs.dsl.api.HelperTypes.HelperInput;
        import cbs.dsl.api.HelperTypes.HelperOutput;
        {{jsonPayloadImport}}        import {{specPackage}}.{{specClass}};
        {{inputTypeImport}}        {{outputTypeImport}}
        {{dslImportsBlock}}        import java.util.Collections;
        import java.util.List;
        import java.util.function.Function;
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
        
            private final {{specClass}} function;
        
            {{parametersField}}
        
            public {{wrapperClass}}(DslComponentResolver resolver) {
                this.function = resolver.resolve({{specClass}}.class)
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
            public HelperOutput preview(HelperInput input) {
                {{inputTypeName}} typed = {{inputConversion}};
                {{outputTypeName}} out = function.preview(typed);
                return {{outputConversion}};
            }
        
            @Override
            public HelperOutput execute(HelperInput input) {
                {{inputTypeName}} typed = {{inputConversion}};
                {{outputTypeName}} out = function.execute(typed);
                return {{outputConversion}};
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
            Map.entry("specPackage", spec.packageName()),
            Map.entry("specClass", spec.className()),
            Map.entry("inputTypeImport", inputTypeImport),
            Map.entry("outputTypeImport", outputTypeImport),
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
            Map.entry("outputConversion", outputConversion),
            Map.entry("dslBody", dslBody)));
  }

  public List<FileWrite> generateFileSpecs(RegistrationModel spec, Path outputDir) {
    List<FileWrite> files = new ArrayList<>();
    String definitionSource = generateDefinitionCode(spec);
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
