package cbs.dsl.codegen;

import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
        : MessageFormat.format(
            "JsonPayload.fromMap(input.params(), {0}.class)", simpleName(spec.inputType()));

    String outputConversion =
        outputIsRuntime ? "out" : "new WorkflowOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : MessageFormat.format("import {0};\n", spec.inputType());
    String outputTypeImport =
        outputIsRuntime ? "" : MessageFormat.format("import {0};\n", spec.outputType());

    String sourceTemplate = """
        package {0};

        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.WorkflowDefinition;
        import cbs.dsl.api.WorkflowTypes.WorkflowInput;
        import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
        import cbs.dsl.api.TransitionRuleDefinition;
        import cbs.dsl.api.ParameterDefinition;
        {1}        import {2}.{3};
        {4}{5}        import java.util.Collections;
        import java.util.List;

        /**
         * Generated WorkflowDefinition wrapper for {6}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.WorkflowCodeGenerator",
            date = "{7}"
        )
        public class {8} implements WorkflowDefinition {

            private final {9} function;

            public {10}() {
                this(null);
            }

            public {10}(DslComponentResolver resolver) {
                this.function = resolver != null ? resolver.resolve({9}.class) : new {9}();
            }

            @Override
            public String getCode() {
                return "{11}";
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
                {12} typed = {13};
                {14} out = function.execute(typed);
                return {15};
            }

            @Override
            public DslObject dsl() {
                return new cbs.dsl.builder.WorkflowDslObject(
                    getCode(),
                    getStates(),
                    getInitial(),
                    getTerminalStates(),
                    getTransitions()
                ) {
                    @Override
                    public cbs.dsl.api.WorkflowTypes.WorkflowOutput execute(cbs.dsl.api.WorkflowTypes.WorkflowInput input) {
                        return {16}.this.execute(input);
                    }
                };
            }
        }
        """;

    String source = MessageFormat.format(
        sourceTemplate,
        DEFINITIONS_PACKAGE,
        jsonPayloadImport,
        spec.packageName(),
        spec.className(),
        inputTypeImport,
        outputTypeImport,
        spec.className(),
        timestamp,
        wrapperClassName,
        spec.className(),
        wrapperClassName,
        wrapperClassName,
        spec.className(),
        spec.className(),
        spec.code(),
        simpleName(spec.inputType()),
        inputConversion,
        simpleName(spec.outputType()),
        outputConversion,
        wrapperClassName);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
