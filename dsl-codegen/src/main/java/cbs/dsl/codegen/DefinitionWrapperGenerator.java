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
  private static final String EV_INPUT = "cbs.dsl.api.EventTypes.EventInput";
  private static final String EV_OUTPUT = "cbs.dsl.api.EventTypes.EventOutput";
  private static final String WF_INPUT = "cbs.dsl.api.WorkflowTypes.WorkflowInput";
  private static final String WF_OUTPUT = "cbs.dsl.api.WorkflowTypes.WorkflowOutput";
  private static final String MO_INPUT = "cbs.dsl.api.MassOperationTypes.MassOperationInput";
  private static final String MO_OUTPUT = "cbs.dsl.api.MassOperationTypes.MassOperationOutput";

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
          case EVENT -> generateEventWrapper(spec, wrapperClassName);
          case WORKFLOW -> generateWorkflowWrapper(spec, wrapperClassName);
          case MASS_OPERATION -> generateMassOperationWrapper(spec, wrapperClassName);
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
        : "JsonPayload.fromMap(input.params(), %s.class)".formatted(simpleName(spec.inputType()));

    String outputConversion =
        outputIsRuntime ? "out" : "new TransactionOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime ? "" : "import " + spec.inputType() + ";\n";
    String outputTypeImport = outputIsRuntime ? "" : "import " + spec.outputType() + ";\n";

    return """
        package %s;

        import cbs.dsl.api.TransactionDefinition;
        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.dsl.api.TransactionTypes.TransactionOutput;
        %s        import %s.%s;
        %s%s
        /**
         * Generated TransactionDefinition wrapper for %s.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.DefinitionWrapperGenerator",
            date = "%s"
        )
        public class %s implements TransactionDefinition {

            private final %s function = new %s();

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
            jsonPayloadImport,
            spec.packageName(),
            spec.className(),
            inputTypeImport,
            outputTypeImport,
            spec.className(),
            timestamp,
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
        : "JsonPayload.fromMap(input.params(), %s.class)".formatted(simpleName(spec.inputType()));

    String outputConversion = outputIsRuntime ? "out" : "new HelperOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime ? "" : "import " + spec.inputType() + ";\n";
    String outputTypeImport = outputIsRuntime ? "" : "import " + spec.outputType() + ";\n";

    return """
        package %s;

        import cbs.dsl.api.HelperDefinition;
        import cbs.dsl.api.HelperTypes.HelperInput;
        import cbs.dsl.api.HelperTypes.HelperOutput;
        %s        import %s.%s;
        %s%s
        /**
         * Generated HelperDefinition wrapper for %s.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.DefinitionWrapperGenerator",
            date = "%s"
        )
        public class %s implements HelperDefinition {

            private final %s function = new %s();

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
            jsonPayloadImport,
            spec.packageName(),
            spec.className(),
            inputTypeImport,
            outputTypeImport,
            spec.className(),
            timestamp,
            wrapperClassName,
            spec.className(),
            spec.className(),
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
        : "JsonPayload.fromMap(input.params(), %s.class)".formatted(simpleName(spec.inputType()));

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime ? "" : "import " + spec.inputType() + ";\n";

    return """
        package %s;

        import cbs.dsl.api.ConditionDefinition;
        import cbs.dsl.api.ConditionTypes.ConditionInput;
        import cbs.dsl.api.ConditionTypes.ConditionOutput;
        import cbs.dsl.api.context.TransactionContext;
        %s        import %s.%s;
        %s        import %s;
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

            private final %s function = new %s();

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
            jsonPayloadImport,
            spec.packageName(),
            spec.className(),
            inputTypeImport,
            spec.outputType(),
            spec.className(),
            timestamp,
            wrapperClassName,
            spec.className(),
            spec.className(),
            spec.code(),
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()));
  }

  private String generateEventWrapper(RegistrationSpec spec, String wrapperClassName) {
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(EV_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(EV_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : "JsonPayload.fromMap(input.params(), %s.class)".formatted(simpleName(spec.inputType()));

    String outputConversion = outputIsRuntime ? "out" : "new EventOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime ? "" : "import " + spec.inputType() + ";\n";
    String outputTypeImport = outputIsRuntime ? "" : "import " + spec.outputType() + ";\n";

    return """
        package %s;

        import cbs.dsl.api.EventDefinition;
        import cbs.dsl.api.EventTypes.EventInput;
        import cbs.dsl.api.EventTypes.EventOutput;
        %s        import %s.%s;
        %s%s
        /**
         * Generated EventDefinition wrapper for %s.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.DefinitionWrapperGenerator",
            date = "%s"
        )
        public class %s implements EventDefinition {

            private final %s function = new %s();

            @Override
            public String getCode() {
                return "%s";
            }

            @Override
            public EventOutput execute(EventInput input) {
                %s typed = %s;
                %s out = function.execute(typed);
                return %s;
            }
        }
        """.formatted(
            GENERATED_PACKAGE,
            jsonPayloadImport,
            spec.packageName(),
            spec.className(),
            inputTypeImport,
            outputTypeImport,
            spec.className(),
            timestamp,
            wrapperClassName,
            spec.className(),
            spec.className(),
            spec.code(),
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()),
            outputConversion);
  }

  private String generateWorkflowWrapper(RegistrationSpec spec, String wrapperClassName) {
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(WF_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(WF_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : "JsonPayload.fromMap(input.params(), %s.class)".formatted(simpleName(spec.inputType()));

    String outputConversion =
        outputIsRuntime ? "out" : "new WorkflowOutput(out.nextState(), out.events(), out.status())";

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime ? "" : "import " + spec.inputType() + ";\n";
    String outputTypeImport = outputIsRuntime ? "" : "import " + spec.outputType() + ";\n";

    return """
        package %s;

        import cbs.dsl.api.WorkflowDefinition;
        import cbs.dsl.api.WorkflowTypes.WorkflowInput;
        import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
        import cbs.dsl.api.TransitionRuleDefinition;
        import cbs.dsl.api.ParameterDefinition;
        %s        import %s.%s;
        %s%s        import java.util.List;

        /**
         * Generated WorkflowDefinition wrapper for %s.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.DefinitionWrapperGenerator",
            date = "%s"
        )
        public class %s implements WorkflowDefinition {

            private final %s function = new %s();

            @Override
            public String getCode() {
                return "%s";
            }

            @Override
            public WorkflowOutput execute(WorkflowInput input) {
                %s typed = %s;
                %s out = function.execute(typed);
                return %s;
            }

            @Override
            public List<String> getStates() {
                return List.of();
            }

            @Override
            public String getInitial() {
                return "";
            }

            @Override
            public List<String> getTerminalStates() {
                return List.of();
            }

            @Override
            public List<TransitionRuleDefinition> getTransitions() {
                return List.of();
            }
        }
        """.formatted(
            GENERATED_PACKAGE,
            jsonPayloadImport,
            spec.packageName(),
            spec.className(),
            inputTypeImport,
            outputTypeImport,
            spec.className(),
            timestamp,
            wrapperClassName,
            spec.className(),
            spec.className(),
            spec.code(),
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()),
            outputConversion);
  }

  private String generateMassOperationWrapper(RegistrationSpec spec, String wrapperClassName) {
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(MO_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(MO_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : "JsonPayload.fromMap(input.params(), %s.class)".formatted(simpleName(spec.inputType()));

    String outputConversion = outputIsRuntime
        ? "out"
        : "new MassOperationOutput(out.processedCount(), out.failedCount(), out.status())";

    String jsonPayloadImport = inputIsRuntime ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport = inputIsRuntime ? "" : "import " + spec.inputType() + ";\n";
    String outputTypeImport = outputIsRuntime ? "" : "import " + spec.outputType() + ";\n";

    return """
        package %s;

        import cbs.dsl.api.MassOperationDefinition;
        import cbs.dsl.api.MassOperationTypes.MassOperationInput;
        import cbs.dsl.api.MassOperationTypes.MassOperationOutput;
        import cbs.dsl.api.LockDefinition;
        import cbs.dsl.api.TriggerDefinition;
        import cbs.dsl.api.SourceDefinition;
        %s        import %s.%s;
        %s%s        import java.util.List;
        import java.util.function.Consumer;
        import cbs.dsl.api.context.MassOperationContext;

        /**
         * Generated MassOperationDefinition wrapper for %s.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.DefinitionWrapperGenerator",
            date = "%s"
        )
        public class %s implements MassOperationDefinition {

            private final %s function = new %s();

            @Override
            public String getCode() {
                return "%s";
            }

            @Override
            public String getCategory() {
                return "DEFAULT";
            }

            @Override
            public List<TriggerDefinition> getTriggers() {
                return List.of();
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
                %s typed = %s;
                %s out = function.execute(typed);
                return %s;
            }
        }
        """.formatted(
            GENERATED_PACKAGE,
            jsonPayloadImport,
            spec.packageName(),
            spec.className(),
            inputTypeImport,
            outputTypeImport,
            spec.className(),
            timestamp,
            wrapperClassName,
            spec.className(),
            spec.className(),
            spec.code(),
            simpleName(spec.inputType()),
            inputConversion,
            simpleName(spec.outputType()),
            outputConversion);
  }

  private String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
