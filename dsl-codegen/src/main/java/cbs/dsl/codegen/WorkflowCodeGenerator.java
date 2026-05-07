package cbs.dsl.codegen;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Domain-oriented generator for {@code @DslComponent(type = WORKFLOW)} components.
 *
 * <p>Produces a single artifact per component: {@code {Code}WorkflowDefinition} — implements
 * {@code WorkflowDefinition} and hosts {@code dsl()} returning a {@code DslObject}.
 */
public class WorkflowCodeGenerator {

  private static final String WF_INPUT = "cbs.dsl.api.WorkflowTypes.WorkflowInput";
  private static final String WF_OUTPUT = "cbs.dsl.api.WorkflowTypes.WorkflowOutput";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;
  private final Function<RegistrationSpec, String> dslBodyProvider;

  public WorkflowCodeGenerator(Function<RegistrationSpec, String> dslBodyProvider) {
    this(null, dslBodyProvider);
  }

  public WorkflowCodeGenerator(Filer filer, Function<RegistrationSpec, String> dslBodyProvider) {

    this.filer = filer;
    this.dslBodyProvider = dslBodyProvider;
  }

  public void generate(List<RegistrationSpec> specs) throws IOException {
    for (RegistrationSpec spec : specs) {
      String definitionSource = generateDefinitionCode(spec);
      writeDefinition(spec, definitionSource);
    }
  }

  public String generateDefinitionCode(RegistrationSpec spec) {
    String wrapperClassName = spec.className() + "Definition";

    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(WF_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(WF_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputSimpleName}}.class)",
            Map.of("inputSimpleName", simpleName(spec.inputType())));

    String outputConversion =
        outputIsRuntime ? "out" : "new WorkflowOutput(JsonPayload.params(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime
        ? ""
        : Substitutor.format("import {{inputType}};\n", Map.of("inputType", spec.inputType()));
    String outputTypeImport = outputIsRuntime
        ? ""
        : Substitutor.format("import {{outputType}};\n", Map.of("outputType", spec.outputType()));

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
        {{jsonPayloadImport}}        import {{specPackageName}}.{{specClassName}};
        {{inputTypeImport}}{{outputTypeImport}}        {{dslImportsBlock}}        import java.util.Collections;
        import java.util.List;
        import javax.annotation.processing.Generated;

        /**
         * Generated WorkflowDefinition wrapper for {{specClassName}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @Generated(
            value = "cbs.dsl.codegen.WorkflowCodeGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClassName}} implements WorkflowDefinition {

            private final {{specClassName}} function;

            public {{wrapperClassName}}() {
                this(null);
            }

            public {{wrapperClassName}}(DslComponentResolver resolver) {
                this.function = resolver != null ? resolver.resolve({{specClassName}}.class) : new {{specClassName}}();
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
    params.put("specPackageName", spec.packageName());
    params.put("specClassName", spec.className());
    params.put("inputTypeImport", inputTypeImport);
    params.put("outputTypeImport", outputTypeImport);
    params.put("timestamp", timestamp);
    params.put("wrapperClassName", wrapperClassName);
    params.put("specCode", spec.code());
    params.put("inputSimpleName", simpleName(spec.inputType()));
    params.put("inputConversion", inputConversion);
    params.put("outputSimpleName", simpleName(spec.outputType()));
    params.put("outputConversion", outputConversion);
    params.put("dslBody", dslBody);
    params.put("dslImportsBlock", dslImportsBlock);
    return Substitutor.format(sourceTemplate, params);
  }

  public void writeDefinitionToPath(RegistrationSpec spec, String source, Path outputDir)
      throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    Path outputPath = outputDir
        .resolve("cbs/dsl/codegen/generated/definitions")
        .resolve(wrapperClassName + ".java");
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, source);
  }

  public List<GeneratedFile> generateFileSpecs(RegistrationSpec spec, Path outputDir) {
    String definitionSource = generateDefinitionCode(spec);
    return List.of(writeDefinitionToSpec(spec, definitionSource, outputDir));
  }

  private GeneratedFile writeDefinitionToSpec(
      RegistrationSpec spec, String source, Path outputDir) {
    String wrapperClassName = spec.className() + "Definition";
    Path outputPath = outputDir
        .resolve("cbs/dsl/codegen/generated/definitions")
        .resolve(wrapperClassName + ".java");
    return new GeneratedFile(outputPath, source);
  }

  public record GeneratedFile(Path path, String content) {}

  public void writeDefinition(RegistrationSpec spec, String source) throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    String qualifiedName = DEFINITIONS_PACKAGE + "." + wrapperClassName;
    JavaFileObject file = filer.createSourceFile(qualifiedName);
    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
