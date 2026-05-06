package cbs.dsl.codegen;

import java.text.MessageFormat;
import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Domain-oriented generator for {@code @DslComponent(type = MASS_OPERATION)} components.
 *
 * <p>Produces a single artifact per component: {@code {Code}MassOperationDefinition} — implements
 * {@code MassOperationDefinition} and hosts {@code dsl()} returning a {@code DslObject}.
 */
@RequiredArgsConstructor
public class MassOperationCodeGenerator {

  private static final String MO_INPUT = "cbs.dsl.api.MassOperationTypes.MassOperationInput";
  private static final String MO_OUTPUT = "cbs.dsl.api.MassOperationTypes.MassOperationOutput";
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
    boolean inputIsRuntime = spec.inputType().equals(MO_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(MO_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : MessageFormat.format(
            "JsonPayload.fromMap(input.params(), {0}.class)", simpleName(spec.inputType()));

    String outputConversion = outputIsRuntime
        ? "out"
        : "new MassOperationOutput(out.processedCount(), out.failedCount(), out.status())";

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : MessageFormat.format("import {0};\n", spec.inputType());
    String outputTypeImport =
        outputIsRuntime ? "" : MessageFormat.format("import {0};\n", spec.outputType());

    String dslBodyOrFallback = (spec.dslBody() != null && !spec.dslBody().isBlank())
        ? spec.dslBody()
        : "return MassOperationDsl.massOperation(\"" + spec.code() + "\").build();";
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : ((spec.dslBody() == null || spec.dslBody().isBlank()) ? "import cbs.dsl.builder.MassOperationDsl;\n" : "");

    String sourceTemplate = //language=java
        """
        package {0};

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
        {1}        import {2}.{3};
        {4}{5}        {19}        import java.util.Collections;
        import java.util.List;
        import java.util.function.Consumer;

        /**
         * Generated MassOperationDefinition wrapper for {6}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.MassOperationCodeGenerator",
            date = "{7}"
        )
        public class {8} implements MassOperationDefinition {

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
                {12} typed = {13};
                {14} out = function.execute(typed);
                return {15};
            }

            @Override
            public DslObject dsl() {
                {16}
            }
        }
        """;

    String source = MessageFormat.format(
        sourceTemplate,
        DEFINITIONS_PACKAGE,           // {0}
        jsonPayloadImport,             // {1}
        spec.packageName(),            // {2}
        spec.className(),              // {3}
        inputTypeImport,               // {4}
        outputTypeImport,              // {5}
        spec.className(),              // {6}
        timestamp,                     // {7}
        wrapperClassName,              // {8}
        spec.className(),              // {9}
        wrapperClassName,              // {10}
        spec.code(),                   // {11}
        simpleName(spec.inputType()),  // {12}
        inputConversion,               // {13}
        simpleName(spec.outputType()), // {14}
        outputConversion,              // {15}
        dslBodyOrFallback,             // {16}
        "",                            // {17} unused
        "",                            // {18} unused
        dslImportsBlock);              // {19}

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
