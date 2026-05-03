package cbs.dsl.codegen;

import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
public class DefinitionWrapperGenerator {

  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private static final String TX_INPUT = "cbs.dsl.api.TransactionTypes.TransactionInput";
  private static final String TX_OUTPUT = "cbs.dsl.api.TransactionTypes.TransactionOutput";
  private static final String HL_INPUT = "cbs.dsl.api.HelperTypes.HelperInput";
  private static final String HL_OUTPUT = "cbs.dsl.api.HelperTypes.HelperOutput";
  private static final String CN_INPUT = "cbs.dsl.api.ConditionTypes.ConditionInput";
  private static final String CN_OUTPUT = "cbs.dsl.api.ConditionTypes.ConditionOutput";

  private final Filer filer;

  public void generate(List<RegistrationSpec> registrations) throws IOException {
    for (RegistrationSpec spec : registrations) {
      generateWrapper(spec);
    }
  }

  private void generateWrapper(RegistrationSpec spec) throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    String qualifiedName = GENERATED_PACKAGE + "." + wrapperClassName;
    JavaFileObject file = filer.createSourceFile(qualifiedName);

    String sourceCode =
        switch (spec.interfaceType()) {
          case TRANSACTION -> generateTransactionWrapper(spec, wrapperClassName);
          case HELPER -> generateHelperWrapper(spec, wrapperClassName);
          case CONDITION -> generateConditionWrapper(spec, wrapperClassName);
        };

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(sourceCode);
    }
  }

  private String generateTransactionWrapper(RegistrationSpec spec, String wrapperClassName) {
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(TX_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(TX_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : "JSONB.type(%s.class).fromJson(JSONB.toJson(input.params()))"
            .formatted(simpleName(spec.inputType()));

    String outputConversion = outputIsRuntime
        ? "out"
        : "new TransactionOutput(JSONB.type(Map.class).fromJson(JSONB.toJson(out)))";

    String jsonbField = (inputIsRuntime && outputIsRuntime)
        ? ""
        : "\n    private static final Jsonb JSONB = Jsonb.builder().build();";
    String jsonbImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import io.avaje.jsonb.Jsonb;\n";
    String mapImport = outputIsRuntime ? "" : "import java.util.Map;\n";

    return """
        package %s;

        import cbs.dsl.api.TransactionDefinition;
        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.dsl.api.TransactionTypes.TransactionOutput;
        %s%s        import %s.%s;
        import %s;
        import %s;

        /**
         * Generated TransactionDefinition wrapper for %s.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.DefinitionWrapperGenerator",
            date = "%s"
        )
        public class %s implements TransactionDefinition {

            private final %s function = new %s();%s

            @Override
            public String getCode() {
                return "%s";
            }

            @Override
            public TransactionOutput preview(TransactionInput input) {
                %s typed = %s;
                %s out = function.preview(typed);
                return %s;
            }

            @Override
            public TransactionOutput execute(TransactionInput input) {
                %s typed = %s;
                %s out = function.execute(typed);
                return %s;
            }

            @Override
            public TransactionOutput rollback(TransactionInput input) {
                %s typed = %s;
                %s out = function.rollback(typed);
                return %s;
            }
        }
        """.formatted(
            GENERATED_PACKAGE,
            jsonbImport,
            mapImport,
            spec.packageName(),
            spec.className(),
            spec.inputType(),
            spec.outputType(),
            spec.className(),
            timestamp,
            wrapperClassName,
            spec.className(),
            spec.className(),
            jsonbField,
            spec.code(),
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()),
            outputConversion,
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()),
            outputConversion,
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()),
            outputConversion);
  }

  private String generateHelperWrapper(RegistrationSpec spec, String wrapperClassName) {
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(HL_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(HL_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : "JSONB.type(%s.class).fromJson(JSONB.toJson(input.params()))"
            .formatted(simpleName(spec.inputType()));

    String outputConversion = outputIsRuntime ? "out" : "new HelperOutput(out)";

    String jsonbField = (inputIsRuntime && outputIsRuntime)
        ? ""
        : "\n    private static final Jsonb JSONB = Jsonb.builder().build();";
    String jsonbImport = inputIsRuntime ? "" : "import io.avaje.jsonb.Jsonb;\n";

    return """
        package %s;

        import cbs.dsl.api.HelperDefinition;
        import cbs.dsl.api.HelperTypes.HelperInput;
        import cbs.dsl.api.HelperTypes.HelperOutput;
        %s        import %s.%s;
        import %s;
        import %s;

        /**
         * Generated HelperDefinition wrapper for %s.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.DefinitionWrapperGenerator",
            date = "%s"
        )
        public class %s implements HelperDefinition {

            private final %s function = new %s();%s

            @Override
            public String getCode() {
                return "%s";
            }

            @Override
            public HelperOutput execute(HelperInput input) {
                %s typed = %s;
                %s out = function.execute(typed);
                return %s;
            }
        }
        """.formatted(
            GENERATED_PACKAGE,
            jsonbImport,
            spec.packageName(),
            spec.className(),
            spec.inputType(),
            spec.outputType(),
            spec.className(),
            timestamp,
            wrapperClassName,
            spec.className(),
            spec.className(),
            jsonbField,
            spec.code(),
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()),
            outputConversion);
  }

  private String generateConditionWrapper(RegistrationSpec spec, String wrapperClassName) {
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(CN_INPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : "JSONB.type(%s.class).fromJson(JSONB.toJson(input.params()))"
            .formatted(simpleName(spec.inputType()));

    String jsonbField =
        inputIsRuntime ? "" : "\n    private static final Jsonb JSONB = Jsonb.builder().build();";
    String jsonbImport = inputIsRuntime ? "" : "import io.avaje.jsonb.Jsonb;\n";

    return """
        package %s;

        import cbs.dsl.api.ConditionDefinition;
        import cbs.dsl.api.ConditionTypes.ConditionInput;
        import cbs.dsl.api.ConditionTypes.ConditionOutput;
        import cbs.dsl.api.context.TransactionContext;
        %s        import %s.%s;
        import %s;
        import %s;
        import java.util.function.Predicate;

        /**
         * Generated ConditionDefinition wrapper for %s.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.DefinitionWrapperGenerator",
            date = "%s"
        )
        public class %s implements ConditionDefinition {

            private final %s function = new %s();%s

            @Override
            public String getCode() {
                return "%s";
            }

            @Override
            public Predicate<TransactionContext> getPredicate() {
                return ctx -> false; // unused — evaluate() is overridden below
            }

            @Override
            public ConditionOutput evaluate(ConditionInput input) {
                %s typed = %s;
                %s out = function.evaluate(typed);
                return new ConditionOutput(out.getValue());
            }
        }
        """.formatted(
            GENERATED_PACKAGE,
            jsonbImport,
            spec.packageName(),
            spec.className(),
            spec.inputType(),
            spec.outputType(),
            spec.className(),
            timestamp,
            wrapperClassName,
            spec.className(),
            spec.className(),
            jsonbField,
            spec.code(),
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()));
  }

  private String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
