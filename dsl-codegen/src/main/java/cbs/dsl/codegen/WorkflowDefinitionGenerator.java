package cbs.dsl.codegen;

import cbs.dsl.codegen.DslCompiler.FileWrite;

import javax.annotation.processing.Filer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class WorkflowDefinitionGenerator implements DefinitionGenerator {

  private static final String WF_INPUT = "cbs.dsl.api.WorkflowTypes.WorkflowInput";
  private static final String WF_OUTPUT = "cbs.dsl.api.WorkflowTypes.WorkflowOutput";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;
  private final Path outputDir;
  private final Function<RegistrationModel, String> dslBodyProvider;

  public WorkflowDefinitionGenerator(Function<RegistrationModel, String> dslBodyProvider) {
    this(null, null, dslBodyProvider);
  }

  public WorkflowDefinitionGenerator(
      Path outputDir, Function<RegistrationModel, String> dslBodyProvider) {
    this(null, outputDir, dslBodyProvider);
  }

  public WorkflowDefinitionGenerator(
      Filer filer, Function<RegistrationModel, String> dslBodyProvider) {
    this(filer, null, dslBodyProvider);
  }

  private WorkflowDefinitionGenerator(
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
    boolean inputIsRuntime = spec.inputType().equals(WF_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(WF_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputSimpleName}}.class)",
            Map.of("inputSimpleName", CodeGenUtil.simpleName(spec.inputType())));

    String outputConversion =
        outputIsRuntime ? "out" : "new WorkflowOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime
        ? ""
        : Substitutor.format("import {{inputType}};\n", Map.of("inputType", spec.inputType()));
    String outputTypeImport = outputIsRuntime
        ? ""
        : Substitutor.format("import {{outputType}};\n", Map.of("outputType", spec.outputType()));

    String specImport = spec.packageName().isBlank()
        ? ""
        : "import " + spec.packageName() + "." + spec.className() + ";\n";

    String dslBody = dslBodyProvider.apply(spec);
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : "import cbs.dsl.builder.UndefinedDslObject;\n";

    String sourceTemplate = // language=java
        """
        package {{definitionsPackage}};

        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.WorkflowDefinition;
        import cbs.dsl.api.WorkflowTypes.WorkflowInput;
        import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
        import cbs.dsl.api.TransitionRuleDefinition;
        import cbs.dsl.api.ParameterDefinition;
        {{jsonPayloadImport}}        {{specImport}}{{inputTypeImport}}{{outputTypeImport}}        {{dslImportsBlock}}        import java.util.Collections;
        import java.util.List;
        import javax.annotation.processing.Generated;
        
        /**
         * Generated WorkflowDefinition wrapper for {{specClassName}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.WorkflowDefinitionGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClassName}} implements WorkflowDefinition {
        
            private final {{specClassName}} function;
        
        
            public {{wrapperClassName}}(DslComponentResolver resolver) {
                this.function = resolver.resolve({{specClassName}}.class)
          }
        
            @Override
            public String getCode() {
                return "{{specCode}}";
            }
        
            @Override
            public List<String> getStates() {
                return Collections.emptyList();
            }
        
            @Override
            public String getInitial() {
                return "";
            }
        
            @Override
            public List<String> getTerminalStates() {
                return Collections.emptyList();
            }
        
            @Override
            public List<TransitionRuleDefinition> getTransitions() {
                return Collections.emptyList();
            }
        
            @Override
            public WorkflowOutput execute(WorkflowInput input) {
                {{inputSimpleName}} typed = {{inputConversion}};
                {{outputSimpleName}} out = function.execute(typed);
                return {{outputConversion}};
            }
        
            @Override
            public DslObject dsl() {
                {{dslBody}}
            }
        }
        """;

    Map<String, String> params = new HashMap<>();
    params.put("definitionsPackage", DEFINITIONS_PACKAGE);
    params.put("jsonPayloadImport", jsonPayloadImport);
    params.put("specImport", specImport);
    params.put("specClassName", spec.className());
    params.put("inputTypeImport", inputTypeImport);
    params.put("outputTypeImport", outputTypeImport);
    params.put("timestamp", timestamp);
    params.put("wrapperClassName", wrapperClassName);
    params.put("specCode", spec.code());
    params.put("inputSimpleName", CodeGenUtil.simpleName(spec.inputType()));
    params.put("inputConversion", inputConversion);
    params.put("outputSimpleName", CodeGenUtil.simpleName(spec.outputType()));
    params.put("outputConversion", outputConversion);
    params.put("dslBody", dslBody);
    params.put("dslImportsBlock", dslImportsBlock);
    return Substitutor.format(sourceTemplate, params);
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
