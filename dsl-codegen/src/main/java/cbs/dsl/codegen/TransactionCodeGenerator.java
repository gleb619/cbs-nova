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
 * Domain-oriented generator for {@code @DslComponent(type = TRANSACTION)} components.
 *
 * <p>Produces two artifacts per component:
 *
 * <ol>
 *   <li>{@code {Code}Activity} — Temporal {@code @ActivityInterface} (thin contract, required by
 *       Temporal SDK).
 *   <li>{@code {Code}TransactionDefinition} — implements both {@code TransactionDefinition} and
 *       {@code {Code}Activity}. This is the single file that hosts the DSL metadata, the business
 *       delegation, and the Temporal activity contract.
 * </ol>
 */
@RequiredArgsConstructor
public class TransactionCodeGenerator {

  private static final String TX_INPUT = "cbs.dsl.api.TransactionTypes.TransactionInput";
  private static final String TX_OUTPUT = "cbs.dsl.api.TransactionTypes.TransactionOutput";
  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;

  public void generate(List<RegistrationSpec> specs) throws IOException {
    for (RegistrationSpec spec : specs) {
      generateActivityInterface(spec);
      generateDefinition(spec);
    }
  }

  private void generateActivityInterface(RegistrationSpec spec) throws IOException {
    String className = spec.className() + "Activity";
    String fqcn = GENERATED_PACKAGE + "." + className;
    JavaFileObject file = filer.createSourceFile(fqcn);
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    String sourceTemplate = //language=java
            """
            package {0};
            
            import cbs.dsl.api.TransactionTypes.TransactionInput;
            import cbs.dsl.api.TransactionTypes.TransactionOutput;
            import io.temporal.activity.ActivityInterface;
            import io.temporal.activity.ActivityMethod;
            
            @javax.annotation.processing.Generated(
                value = "cbs.dsl.codegen.TransactionCodeGenerator",
                date = "{1}"
            )
            @ActivityInterface
            public interface {2} {
            
                @ActivityMethod
                TransactionOutput execute(TransactionInput input);
            }
            """;
    String source = MessageFormat.format(sourceTemplate, GENERATED_PACKAGE, timestamp, className);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private void generateDefinition(RegistrationSpec spec) throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    String activityInterfaceName = spec.className() + "Activity";
    String qualifiedName = DEFINITIONS_PACKAGE + "." + wrapperClassName;
    JavaFileObject file = filer.createSourceFile(qualifiedName);

    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(TX_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(TX_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : MessageFormat.format(
            "JsonPayload.fromMap(input.params(), {0}.class)", simpleName(spec.inputType()));

    String outputConversion =
        outputIsRuntime ? "out" : "new TransactionOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : MessageFormat.format("import {0};\n", spec.inputType());
    String outputTypeImport =
        outputIsRuntime ? "" : MessageFormat.format("import {0};\n", spec.outputType());

    String dslBodyOrFallback = (spec.dslBody() != null && !spec.dslBody().isBlank())
        ? spec.dslBody()
        : "return TransactionDsl.transaction(\"" + spec.code() + "\").build();";
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : ((spec.dslBody() == null || spec.dslBody().isBlank()) ? "import cbs.dsl.builder.TransactionDsl;\n" : "");

    String sourceTemplate = //language=java
        """
        package {0};

        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.TransactionDefinition;
        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.dsl.api.TransactionTypes.TransactionOutput;
        import cbs.dsl.api.context.TransactionContext;
        import {1}.{2};
        {3}        import {4}.{5};
        {6}{7}
        {19}        import java.util.function.Consumer;
        import java.util.function.Function;

        /**
         * Generated TransactionDefinition wrapper + Activity implementation for {8}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.TransactionCodeGenerator",
            date = "{9}"
        )
        public class {10} implements TransactionDefinition, {11} {

            private final {12} function;

            public {13}() {
                this(null);
            }

            public {13}(DslComponentResolver resolver) {
                this.function = resolver != null ? resolver.resolve({14}.class) : new {14}();
            }

            @Override
            public String getCode() {
                return "{15}";
            }

            @Override
            public TransactionOutput preview(TransactionInput input) {
                {16} typed = {17};
                {18} out = function.preview(typed);
                return {20};
            }

            @Override
            public TransactionOutput execute(TransactionInput input) {
                {16} typed = {17};
                {18} out = function.execute(typed);
                return {20};
            }

            @Override
            public TransactionOutput rollback(TransactionInput input) {
                {16} typed = {17};
                {18} out = function.rollback(typed);
                return {20};
            }

            @Override
            public DslObject dsl() {
                {21}
            }
        }
        """;
    String source = MessageFormat.format(sourceTemplate,
        DEFINITIONS_PACKAGE,           // {0}
        GENERATED_PACKAGE,             // {1}
        activityInterfaceName,         // {2}
        jsonPayloadImport,             // {3}
        spec.packageName(),            // {4}
        spec.className(),              // {5}
        inputTypeImport,               // {6}
        outputTypeImport,              // {7}
        spec.className(),              // {8}
        timestamp,                     // {9}
        wrapperClassName,              // {10}
        activityInterfaceName,         // {11}
        spec.className(),              // {12}
        wrapperClassName,              // {13}
        spec.className(),              // {14}
        spec.code(),                   // {15}
        simpleName(spec.inputType()),  // {16}
        inputConversion,               // {17}
        simpleName(spec.outputType()), // {18}
        dslImportsBlock,               // {19}
        outputConversion,              // {20}
        dslBodyOrFallback);            // {21}

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
