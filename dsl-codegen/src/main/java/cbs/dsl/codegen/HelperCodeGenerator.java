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
 * Domain-oriented generator for {@code @DslComponent(type = HELPER)} components.
 *
 * <p>Produces two artifacts per component:
 *
 * <ol>
 *   <li>{@code {Code}Activity} — Temporal {@code @ActivityInterface}.
 *   <li>{@code {Code}HelperDefinition} — implements both {@code HelperDefinition} and
 *       {@code {Code}Activity}. This is the single file that hosts the DSL metadata, the business
 *       delegation, and the Temporal activity contract.
 * </ol>
 */
@RequiredArgsConstructor
public class HelperCodeGenerator {

  private static final String HL_INPUT = "cbs.dsl.api.HelperTypes.HelperInput";
  private static final String HL_OUTPUT = "cbs.dsl.api.HelperTypes.HelperOutput";
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

    String sourceTemplate = """
        package {0};

        import cbs.dsl.api.HelperTypes.HelperInput;
        import cbs.dsl.api.HelperTypes.HelperOutput;
        import io.temporal.activity.ActivityInterface;
        import io.temporal.activity.ActivityMethod;

        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.HelperCodeGenerator",
            date = "{1}"
        )
        @ActivityInterface
        public interface {2} {

            @ActivityMethod
            HelperOutput execute(HelperInput input);
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
    boolean inputIsRuntime = spec.inputType().equals(HL_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(HL_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : MessageFormat.format(
            "JsonPayload.fromMap(input.params(), {0}.class)", simpleName(spec.inputType()));

    String outputConversion = outputIsRuntime ? "out" : "new HelperOutput(JsonPayload.toMap(out))";

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
        import cbs.dsl.api.HelperDefinition;
        import cbs.dsl.api.HelperTypes.HelperInput;
        import cbs.dsl.api.HelperTypes.HelperOutput;
        import {1}.{2};
        {3}        import {4}.{5};
        {6}{7}
        import java.util.function.Function;

        /**
         * Generated HelperDefinition wrapper + Activity implementation for {8}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.HelperCodeGenerator",
            date = "{9}"
        )
        public class {10} implements HelperDefinition, {11} {

            private final {12} function;

            public {13}() {
                this(null);
            }

            public {13}(DslComponentResolver resolver) {
                this.function = resolver != null ? resolver.resolve({12}.class) : new {12}();
            }

            @Override
            public String getCode() {
                return "{14}";
            }

            @Override
            public HelperOutput preview(HelperInput input) {
                {15} typed = {16};
                {17} out = function.preview(typed);
                return {18};
            }

            @Override
            public HelperOutput execute(HelperInput input) {
                {15} typed = {16};
                {17} out = function.execute(typed);
                return {18};
            }

            @Override
            public DslObject dsl() {
                return new cbs.dsl.builder.HelperDslObject(
                    getCode(),
                    getParameters(),
                    input -> preview(input),
                    input -> execute(input)
                );
            }
        }
        """;

    String source = MessageFormat.format(
        sourceTemplate,
        DEFINITIONS_PACKAGE,
        GENERATED_PACKAGE,
        activityInterfaceName,
        jsonPayloadImport,
        spec.packageName(),
        spec.className(),
        inputTypeImport,
        outputTypeImport,
        spec.className(),
        timestamp,
        wrapperClassName,
        activityInterfaceName,
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
        simpleName(spec.inputType()),
        inputConversion,
        simpleName(spec.outputType()),
        outputConversion);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
