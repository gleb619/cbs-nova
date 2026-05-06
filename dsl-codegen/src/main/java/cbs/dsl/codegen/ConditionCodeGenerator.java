package cbs.dsl.codegen;

import java.text.MessageFormat;
import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;

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
        : formatTemplate("JsonPayload.fromMap(input.params(), {0}.class)", simpleName(spec.inputType()));

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : formatTemplate("import {0};\n", spec.inputType());

    String dslBodyOrFallback = (spec.dslBody() != null && !spec.dslBody().isBlank())
        ? spec.dslBody()
        : "return ConditionDsl.condition(\"" + spec.code() + "\").build();";
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : ((spec.dslBody() == null || spec.dslBody().isBlank()) ? "import cbs.dsl.builder.ConditionDsl;\n" : "");

    String sourceTemplate = //language=java
        """
        package {0};

        import cbs.dsl.api.ConditionDefinition;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.ConditionTypes.ConditionInput;
        import cbs.dsl.api.ConditionTypes.ConditionOutput;
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.context.TransactionContext;
        {1}        import {2}.{3};
        {4}        import {5};
        {19}        import java.util.function.Predicate;

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
                this.function = resolver != null ? resolver.resolve({11}.class) : new {12}();
            }

            @Override
            public String getCode() {
                return "{13}";
            }

            @Override
            public Predicate<TransactionContext> getPredicate() {
                return ctx -> false;
            }

            @Override
            public ConditionOutput evaluate(ConditionInput input) {
                {14} typed = {15};
                {16} out = function.evaluate(typed);
                return new ConditionOutput(out.getValue());
            }

            @Override
            public DslObject dsl() {
                {17}
            }
        }
        """;

    String source = formatTemplate(sourceTemplate,
        DEFINITIONS_PACKAGE,           // {0}
        jsonPayloadImport,             // {1}
        spec.packageName(),            // {2}
        spec.className(),              // {3}
        inputTypeImport,               // {4}
        spec.outputType(),             // {5}
        spec.className(),              // {6}
        timestamp,                     // {7}
        wrapperClassName,              // {8}
        spec.className(),              // {9}
        wrapperClassName,              // {10}
        spec.className(),              // {11}
        spec.className(),              // {12}
        spec.code(),                   // {13}
        simpleName(spec.inputType()),  // {14}
        inputConversion,               // {15}
        simpleName(spec.outputType()), // {16}
        dslBodyOrFallback,             // {17}
        "",                            // {18} unused
        dslImportsBlock);              // {19}

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String formatTemplate(String template, Object... args) {
    String result = template;
    for (int i = args.length - 1; i >= 0; i--) {
      result = result.replace("{" + i + "}", String.valueOf(args[i]));
    }
    return result;
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
