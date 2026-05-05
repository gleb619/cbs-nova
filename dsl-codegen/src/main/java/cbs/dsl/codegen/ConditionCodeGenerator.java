package cbs.dsl.codegen;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Domain-oriented generator for {@code @DslComponent(type = CONDITION)} components.
 *
 * <p>Produces a single artifact per component: {@code {Code}ConditionDefinition} — implements
 * {@code ConditionDefinition} and hosts {@code dsl()} returning a {@code DslObject}.
 */
public class ConditionCodeGenerator {

  private static final String CN_INPUT = "cbs.dsl.api.ConditionTypes.ConditionInput";
  private static final String CN_OUTPUT = "cbs.dsl.api.ConditionTypes.ConditionOutput";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;

  public ConditionCodeGenerator(Filer filer) {
    this.filer = filer;
  }

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
    boolean inputIsRuntime = spec.inputType().equals(CN_INPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : MessageFormat.format(
            "JsonPayload.fromMap(input.params(), {0}.class)", simpleName(spec.inputType()));

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : MessageFormat.format("import {0};\n", spec.inputType());

    String sourceTemplate = """
        package {0};

        import cbs.dsl.api.ConditionDefinition;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.ConditionTypes.ConditionInput;
        import cbs.dsl.api.ConditionTypes.ConditionOutput;
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.context.TransactionContext;
        {1}        import {2}.{3};
        {4}        import {5};
        import java.util.function.Predicate;

        /**
         * Generated ConditionDefinition wrapper for {6}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.ConditionCodeGenerator",
            date = "{7}"
        )
        public class {8} implements ConditionDefinition {

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
            public Predicate<TransactionContext> getPredicate() {
                return ctx -> false;
            }

            @Override
            public ConditionOutput evaluate(ConditionInput input) {
                {12} typed = {13};
                {14} out = function.evaluate(typed);
                return new ConditionOutput(out.getValue());
            }

            @Override
            public DslObject dsl() {
                return new cbs.dsl.builder.ConditionDslObject(
                    getCode(),
                    getParameters(),
                    ctx -> evaluate(new ConditionInput(
                        ctx.getEventParameters(), ctx.getEventCode(), ctx.getWorkflowExecutionId()))
                );
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
        spec.outputType(),
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
        simpleName(spec.outputType()));

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
