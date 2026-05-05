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
 * Domain-oriented generator for {@code @DslComponent(type = EVENT)} components.
 *
 * <p>Produces two artifacts per component:
 *
 * <ol>
 *   <li>{@code {Code}Workflow} — Temporal {@code @WorkflowInterface} with a unique
 *       {@code @WorkflowMethod} named after the event code.
 *   <li>{@code {Code}EventDefinition} — implements both {@code EventDefinition} and
 *       {@code {Code}Workflow}. This is the single file that hosts the DSL metadata, the business
 *       delegation, and the Temporal workflow contract.
 * </ol>
 */
public class EventCodeGenerator {

  private static final String EV_INPUT = "cbs.dsl.api.EventTypes.EventInput";
  private static final String EV_OUTPUT = "cbs.dsl.api.EventTypes.EventOutput";
  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";
  private static final String DEFINITIONS_PACKAGE = "cbs.dsl.codegen.generated.definitions";

  private final Filer filer;

  public EventCodeGenerator(Filer filer) {
    this.filer = filer;
  }

  public void generate(List<RegistrationSpec> specs) throws IOException {
    for (RegistrationSpec spec : specs) {
      generateWorkflowInterface(spec);
      generateDefinition(spec);
    }
  }

  private void generateWorkflowInterface(RegistrationSpec spec) throws IOException {
    String className = toClassName(spec.code()) + "Workflow";
    String packageName = GENERATED_PACKAGE;
    String fqcn = packageName + "." + className;

    JavaFileObject file = filer.createSourceFile(fqcn);
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    String sourceTemplate = """
        package {0};

        import cbs.nova.model.EventWorkflowRequest;
        import cbs.nova.model.WorkflowExecutionResponse;
        import io.temporal.workflow.WorkflowInterface;
        import io.temporal.workflow.WorkflowMethod;

        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.EventCodeGenerator",
            date = "{1}"
        )
        @WorkflowInterface
        public interface {2} {

            @WorkflowMethod(name = "{3}")
            WorkflowExecutionResponse execute(EventWorkflowRequest input);
        }
        """;

    String source =
        MessageFormat.format(sourceTemplate, packageName, timestamp, className, spec.code());

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private void generateDefinition(RegistrationSpec spec) throws IOException {
    String wrapperClassName = spec.className() + "Definition";
    String workflowInterfaceName = toClassName(spec.code()) + "Workflow";
    String qualifiedName = DEFINITIONS_PACKAGE + "." + wrapperClassName;
    JavaFileObject file = filer.createSourceFile(qualifiedName);

    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    boolean inputIsRuntime = spec.inputType().equals(EV_INPUT);
    boolean outputIsRuntime = spec.outputType().equals(EV_OUTPUT);

    String inputConversion = inputIsRuntime
        ? "input"
        : MessageFormat.format(
            "JsonPayload.fromMap(input.params(), {0}.class)", simpleName(spec.inputType()));

    String outputConversion = outputIsRuntime ? "out" : "new EventOutput(JsonPayload.toMap(out))";

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
        import cbs.dsl.api.EventDefinition;
        import cbs.dsl.api.EventTypes.EventInput;
        import cbs.dsl.api.EventTypes.EventOutput;
        import cbs.dsl.api.context.DisplayScope;
        import cbs.dsl.api.context.EnrichmentContext;
        import cbs.dsl.api.context.FinishContext;
        import cbs.dsl.api.context.TransactionsScope;
        import cbs.nova.model.EventWorkflowRequest;
        import cbs.nova.model.WorkflowExecutionResponse;
        import cbs.nova.service.EventWorkflowOrchestrator;
        import {1}.{2};
        {3}        import {4}.{5};
        {6}{7}
        import java.util.Collections;
        import java.util.List;
        import java.util.function.BiConsumer;
        import java.util.function.Consumer;

        /**
         * Generated EventDefinition wrapper + Workflow implementation for {8}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.EventCodeGenerator",
            date = "{9}"
        )
        public class {10} implements EventDefinition, {11} {

            private final {12} function;
            private final EventWorkflowOrchestrator orchestrator;

            public {13}() {
                this(null, null);
            }

            public {13}(DslComponentResolver resolver) {
                this(resolver, null);
            }

            public {13}(EventWorkflowOrchestrator orchestrator) {
                this(null, orchestrator);
            }

            public {13}(DslComponentResolver resolver, EventWorkflowOrchestrator orchestrator) {
                this.function = resolver != null ? resolver.resolve({12}.class) : new {12}();
                this.orchestrator = orchestrator;
            }

            @Override
            public String getCode() {
                return "{14}";
            }

            @Override
            public List<cbs.dsl.api.ParameterDefinition> getParameters() {
                return Collections.emptyList();
            }

            @Override
            public Consumer<EnrichmentContext> getContextBlock() {
                return ctx -> {};
            }

            @Override
            public Consumer<DisplayScope> getDisplayBlock() {
                return scope -> {};
            }

            @Override
            public Consumer<TransactionsScope> getTransactionsBlock() {
                return null;
            }

            @Override
            public List<String> getTransactionCodes() {
                return Collections.emptyList();
            }

            @Override
            public BiConsumer<FinishContext, Throwable> getFinishBlock() {
                return (ctx, ex) -> {};
            }

            @Override
            public EventOutput preview(EventInput input) {
                {15} typed = {16};
                {17} out = function.preview(typed);
                return {18};
            }

            @Override
            public EventOutput execute(EventInput input) {
                {15} typed = {16};
                {17} out = function.execute(typed);
                return {18};
            }

            @Override
            public DslObject dsl() {
                return new cbs.dsl.builder.EventDslObject(
                    getCode(),
                    getParameters(),
                    getContextBlock(),
                    getDisplayBlock(),
                    getTransactionsBlock(),
                    getTransactionCodes(),
                    getFinishBlock()
                ) {
                    @Override
                    public cbs.dsl.api.EventTypes.EventOutput execute(cbs.dsl.api.EventTypes.EventInput input) {
                        return {19}.this.execute(input);
                    }
                    @Override
                    public cbs.dsl.api.EventTypes.EventOutput preview(cbs.dsl.api.EventTypes.EventInput input) {
                        return {19}.this.preview(input);
                    }
                };
            }

            @Override
            public WorkflowExecutionResponse execute(EventWorkflowRequest input) {
                if (orchestrator == null) {
                    throw new IllegalStateException("EventWorkflowOrchestrator is required for workflow execution");
                }
                return orchestrator.execute(input, Collections.emptyList());
            }
        }
        """;

    String source = MessageFormat.format(
        sourceTemplate,
        DEFINITIONS_PACKAGE,
        GENERATED_PACKAGE,
        workflowInterfaceName,
        jsonPayloadImport,
        spec.packageName(),
        spec.className(),
        inputTypeImport,
        outputTypeImport,
        spec.className(),
        timestamp,
        wrapperClassName,
        workflowInterfaceName,
        spec.className(),
        wrapperClassName,
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
        outputConversion,
        wrapperClassName,
        wrapperClassName);

    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  private static String toClassName(String code) {
    StringBuilder sb = new StringBuilder();
    boolean upper = true;
    for (char c : code.toCharArray()) {
      if (c == '_' || c == '-') {
        upper = true;
      } else if (upper) {
        sb.append(Character.toUpperCase(c));
        upper = false;
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
  }
}
