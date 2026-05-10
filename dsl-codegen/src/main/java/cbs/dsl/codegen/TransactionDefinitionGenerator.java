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

public class TransactionDefinitionGenerator implements DefinitionGenerator {

  private static final String TX_INPUT = "cbs.dsl.api.TransactionTypes.TransactionInput";
  private static final String TX_OUTPUT = "cbs.dsl.api.TransactionTypes.TransactionOutput";
  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;
  private final Path outputDir;
  private final Function<RegistrationModel, String> dslBodyProvider;

  public TransactionDefinitionGenerator(Function<RegistrationModel, String> dslBodyProvider) {
    this(null, null, dslBodyProvider);
  }

  public TransactionDefinitionGenerator(
      Path outputDir, Function<RegistrationModel, String> dslBodyProvider) {
    this(null, outputDir, dslBodyProvider);
  }

  public TransactionDefinitionGenerator(
      Filer filer, Function<RegistrationModel, String> dslBodyProvider) {
    this(filer, null, dslBodyProvider);
  }

  private TransactionDefinitionGenerator(
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
    String className = spec.className() + "Activity";
    String timestamp = CodeGenUtil.currentTimestamp();

    return Substitutor.format(
        // language=java
        """
        package {{package}};

        import cbs.dsl.api.ContextTypes.ContextOutput;
        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.dsl.api.TransactionTypes.TransactionOutput;
        import io.temporal.activity.ActivityInterface;
        import io.temporal.activity.ActivityMethod;
        import java.util.Map;
        import javax.annotation.processing.Generated;

        @Generated(
            value = "cbs.dsl.codegen.TransactionDefinitionGenerator",
            date = "{{timestamp}}"
        )
        @ActivityInterface
        public interface {{className}} {

            @ActivityMethod
            ContextOutput prepare(Map<String, Object> params);

            @ActivityMethod
            TransactionOutput execute(TransactionInput input);

            @ActivityMethod
            TransactionOutput rollback(TransactionInput input);
        }
        """,
        Map.of(
            "package", GENERATED_PACKAGE,
            "timestamp", timestamp,
            "className", className));
  }

  public void writeActivityInterface(RegistrationModel spec, String source) throws IOException {
    String className = spec.className() + "Activity";
    String fqcn = GENERATED_PACKAGE + "." + className;
    CodeGenUtil.writeToFiler(filer, fqcn, source);
  }

  public String generateDefinitionCode(RegistrationModel spec) {
    String wrapperClassName = spec.className() + "Definition";
    String activityInterfaceName = spec.className() + "Activity";

    String timestamp = CodeGenUtil.currentTimestamp();
    boolean inputIsRuntime = spec.inputType().equals(TX_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(TX_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputType}}.class)",
            Map.of("inputType", CodeGenUtil.simpleName(spec.inputType())));

    String outputConversion =
        outputIsRuntime ? "out" : "TransactionOutput.success(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime ? "" : "import %s;\n".formatted(spec.inputType());
    String parameterScannerImports = inputIsRuntime
        ? ""
        : "import cbs.dsl.api.ParameterScanner;\n        import cbs.dsl.api.ParameterScanner.ParameterScanResult;\n        ";
    String outputTypeImport = outputIsRuntime ? "" : "import %s;\n".formatted(spec.outputType());

    boolean hasCustomInput = !inputIsRuntime;
    String bigDecimalImport = hasCustomInput ? "import java.math.BigDecimal;\n" : "";

    String parametersField = inputIsRuntime
        ? ""
        : "private static final ParameterScanResult PARAMETERS = ParameterScanner.scan(%s.class);\n\n"
            .formatted(CodeGenUtil.simpleName(spec.inputType()));

    String getParametersOverride = inputIsRuntime ? "" : """
          @Override
          public List<ParameterDefinition> getParameters() {
              return PARAMETERS.definitions();
          }
          """;

    String specImport = spec.packageName().isBlank()
        ? ""
        : "import " + spec.packageName() + "." + spec.className() + ";\n";

    String dslBody = dslBodyProvider.apply(spec);
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : "import cbs.dsl.builder.UndefinedDslObject;\n";

    return Substitutor.format(
        // language=java
        """
        package {{definitionsPackage}};

        import cbs.dsl.api.ContextTypes.ContextOutput;
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.JsonPayload;
        import cbs.dsl.api.ParameterDefinition;
        {{parameterScannerImports}}import cbs.dsl.api.TransactionDefinition;
        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.dsl.api.TransactionTypes.TransactionOutput;
        import cbs.dsl.api.ParametersTypes.ParameterError;
        import cbs.dsl.api.ParametersTypes.ParametersInput;
        import cbs.dsl.api.ParametersTypes;
        import {{generatedPackage}}.{{activityInterfaceName}};
        {{specImport}}{{inputTypeImport}}{{outputTypeImport}}
        {{dslImportsBlock}}
        import java.util.ArrayList;
        import java.util.Collections;
        import java.util.List;
        import java.util.Map;
        import javax.annotation.processing.Generated;

        /**
         * Generated TransactionDefinition wrapper for {{specClassName}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.TransactionDefinitionGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClassName}} implements TransactionDefinition, {{activityInterfaceName}} {

            private final {{specClassName}} function;

            {{parametersField}}

            public {{wrapperClassName}}(DslComponentResolver resolver) {
                this.function = resolver.resolve({{specClassName}}.class);
            }

            @Override
            public String getCode() {
                return "{{specCode}}";
            }

            {{getParametersOverride}}
            @Override
            public ContextOutput prepare(Map<String, Object> params) {
                return prepareContext(params);
            }

            @Override
            public TransactionOutput execute(TransactionInput input) {
                {{inputSimpleName}} typed = {{inputConversion}};
                {{outputSimpleName}} out = function.execute(typed);
                return {{outputConversion}};
            }

            @Override
            public TransactionOutput rollback(TransactionInput input) {
                {{inputSimpleName}} typed = {{inputConversion}};
                {{outputSimpleName}} out = function.rollback(typed);
                return {{outputConversion}};
            }

            @Override
            public DslObject dsl() {
                {{dslBody}}
            }
        }
        """,
        Map.ofEntries(
            Map.entry("definitionsPackage", DEFINITIONS_PACKAGE),
            Map.entry("generatedPackage", GENERATED_PACKAGE),
            Map.entry("activityInterfaceName", activityInterfaceName),
            Map.entry("specImport", specImport),
            Map.entry("specClassName", spec.className()),
            Map.entry("inputTypeImport", inputTypeImport),
            Map.entry("outputTypeImport", outputTypeImport),
            Map.entry("timestamp", timestamp),
            Map.entry("wrapperClassName", wrapperClassName),
            Map.entry("specCode", spec.code()),
            Map.entry("inputSimpleName", CodeGenUtil.simpleName(spec.inputType())),
            Map.entry("inputConversion", inputConversion),
            Map.entry("outputSimpleName", CodeGenUtil.simpleName(spec.outputType())),
            Map.entry("outputConversion", outputConversion),
            Map.entry("parameterScannerImports", parameterScannerImports),
            Map.entry("parametersField", parametersField),
            Map.entry("getParametersOverride", getParametersOverride),
            Map.entry("dslImportsBlock", dslImportsBlock),
            Map.entry("dslBody", dslBody),
            Map.entry("bigDecimalImport", bigDecimalImport)));
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
    String className = spec.className() + "Activity";
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
}
