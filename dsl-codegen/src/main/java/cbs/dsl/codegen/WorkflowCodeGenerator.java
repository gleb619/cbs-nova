package cbs.dsl.codegen;

import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain-oriented generator for {@code @DslComponent(type = WORKFLOW)} components.
 *
 * <p>Produces a single artifact per component: {@code {Code}WorkflowDefinition} — implements
 * {@code WorkflowDefinition} and hosts {@code dsl()} returning a {@code DslObject}.
 */
@RequiredArgsConstructor
public class WorkflowCodeGenerator {

  private static final String WF_INPUT = "cbs.dsl.api.WorkflowTypes.WorkflowInput";
  private static final String WF_OUTPUT = "cbs.dsl.api.WorkflowTypes.WorkflowOutput";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;

  public void generate(List<RegistrationSpec> specs) throws IOException {
    for (RegistrationSpec spec : specs) {
      generateDefinition(spec);
    }
  }

  private void generateDefinition(RegistrationSpec spec) throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    String qualifiedName = DEFINITIONS_PACKAGE + "." + wrapperClassName;
    JavaFileObject file = filer.createSourceFile(qualifiedName);

    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(WF_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(WF_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : Substitutor.format(
            "JsonPayload.fromMap(input.params(), {{inputSimpleName}}.class)", Map.of("inputSimpleName", simpleName(spec.inputType())));

    String outputConversion =
        outputIsRuntime ? "out" : "new WorkflowOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : Substitutor.format("import {{inputType}};\n", Map.of("inputType", spec.inputType()));
    String outputTypeImport =
        outputIsRuntime ? "" : Substitutor.format("import {{outputType}};\n", Map.of("outputType", spec.outputType()));

    String dslBodyOrFallback = (spec.dslBody() != null && !spec.dslBody().isBlank())
        ? spec.dslBody()
        : "return WorkflowDsl.workflow(\"" + spec.code() + "\").build();";
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : ((spec.dslBody() == null || spec.dslBody().isBlank()) ? "import cbs.dsl.builder.WorkflowDsl;\n" : "");

    String sourceTemplate = //language=java
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
                {{dslBodyOrFallback}}
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
    params.put("dslBodyOrFallback", dslBodyOrFallback);
    params.put("dslImportsBlock", dslImportsBlock);
    String source = Substitutor.format(sourceTemplate, params);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
