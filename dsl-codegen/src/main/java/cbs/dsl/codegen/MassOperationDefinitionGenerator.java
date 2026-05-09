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

public class MassOperationDefinitionGenerator implements DefinitionGenerator {

  private static final String MO_INPUT = "cbs.dsl.api.MassOperationTypes.MassOperationInput";
  private static final String MO_OUTPUT = "cbs.dsl.api.MassOperationTypes.MassOperationOutput";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;
  private final Path outputDir;
  private final Function<RegistrationModel, String> dslBodyProvider;

  public MassOperationDefinitionGenerator(Function<RegistrationModel, String> dslBodyProvider) {
    this(null, null, dslBodyProvider);
  }

  public MassOperationDefinitionGenerator(
      Path outputDir, Function<RegistrationModel, String> dslBodyProvider) {
    this(null, outputDir, dslBodyProvider);
  }

  public MassOperationDefinitionGenerator(
      Filer filer, Function<RegistrationModel, String> dslBodyProvider) {
    this(filer, null, dslBodyProvider);
  }

  private MassOperationDefinitionGenerator(
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
    boolean inputIsRuntime = spec.inputType().equals(MO_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(MO_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputType}}.class)",
            Map.of("inputType", CodeGenUtil.simpleName(spec.inputType())));

    String outputConversion = outputIsRuntime
        ? "out"
        : "new MassOperationOutput(out.processedCount(), out.failedCount(), out.status())";

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime
        ? ""
        : Substitutor.format("import {{type}};\n", Map.of("type", spec.inputType()));
    String outputTypeImport = outputIsRuntime
        ? ""
        : Substitutor.format("import {{type}};\n", Map.of("type", spec.outputType()));

    String dslBody = dslBodyProvider.apply(spec);
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : "import cbs.dsl.builder.UndefinedDslObject;\n";

    String sourceTemplate = // language=java
        """
        package {{package}};
        
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.LockDefinition;
        import cbs.dsl.api.MassOperationDefinition;
        import cbs.dsl.api.MassOperationTypes.MassOperationInput;
        import cbs.dsl.api.MassOperationTypes.MassOperationOutput;
        import cbs.dsl.api.SignalTypes;
        import cbs.dsl.api.SourceDefinition;
        import cbs.dsl.api.TriggerDefinition;
        import cbs.dsl.api.context.MassOperationContext;
        {{jsonPayloadImport}}        import {{packageName}}.{{className}};
        {{inputTypeImport}}{{outputTypeImport}}        {{dslImports}}        import java.util.Collections;
        import java.util.List;
        import java.util.function.Consumer;
        import javax.annotation.processing.Generated;
        
        /**
         * Generated MassOperationDefinition wrapper for {{className}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.MassOperationDefinitionGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClassName}} implements MassOperationDefinition {
        
            private final {{className}} function;
        
        
            public {{wrapperClassName}}(DslComponentResolver resolver) {
                this.function = resolver.resolve({{className}}.class)
          }
        
            @Override
            public String getCode() {
                return "{{code}}";
            }
        
            @Override
            public String getCategory() {
                return "DEFAULT";
            }
        
            @Override
            public List<TriggerDefinition> getTriggers() {
                return Collections.emptyList();
            }
        
            @Override
            public SourceDefinition getSource() {
                return null;
            }
        
            @Override
            public Consumer<MassOperationContext> getItemBlock() {
                return ctx -> {};
            }
        
            @Override
            public MassOperationOutput execute(MassOperationInput input) {
                {{inputType}} typed = {{inputConversion}};
                {{outputType}} out = function.execute(typed);
                return {{outputConversion}};
            }
        
            @Override
            public DslObject dsl() {
                {{dslBody}}
            }
        }
        """;

    return Substitutor.format(
        sourceTemplate,
        Map.ofEntries(
            Map.entry("package", DEFINITIONS_PACKAGE),
            Map.entry("jsonPayloadImport", jsonPayloadImport),
            Map.entry("packageName", spec.packageName()),
            Map.entry("className", spec.className()),
            Map.entry("inputTypeImport", inputTypeImport),
            Map.entry("outputTypeImport", outputTypeImport),
            Map.entry("timestamp", timestamp),
            Map.entry("wrapperClassName", wrapperClassName),
            Map.entry("code", spec.code()),
            Map.entry("inputType", CodeGenUtil.simpleName(spec.inputType())),
            Map.entry("inputConversion", inputConversion),
            Map.entry("outputType", CodeGenUtil.simpleName(spec.outputType())),
            Map.entry("outputConversion", outputConversion),
            Map.entry("dslBody", dslBody),
            Map.entry("dslImports", dslImportsBlock)));
  }

  public List<FileWrite> generateFileSpecs(RegistrationModel spec, Path outputDir) {
    String definitionSource = generateDefinitionCode(spec);
    return List.of(writeDefinitionToSpec(spec, definitionSource, outputDir));
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
