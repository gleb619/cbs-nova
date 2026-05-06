package cbs.dsl.codegen;


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

    String sourceTemplate = //language=java
        """
        package {{package}};
        
        import cbs.nova.model.EventWorkflowRequest;
        import cbs.nova.model.WorkflowExecutionResponse;
        import io.temporal.workflow.WorkflowInterface;
        import io.temporal.workflow.WorkflowMethod;
        import javax.annotation.processing.Generated;
        
        @Generated(
            value = "cbs.dsl.codegen.EventCodeGenerator",
            date = "{{timestamp}}"
        )
        @WorkflowInterface
        public interface {{className}} {
        
            @WorkflowMethod(name = "{{workflowMethodName}}")
            WorkflowExecutionResponse execute(EventWorkflowRequest input);
        }
        """;

    String source = Substitutor.format(sourceTemplate, Map.of(
        "package", packageName,
        "timestamp", timestamp,
        "className", className,
        "workflowMethodName", spec.code()));

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
        : Substitutor.format("JsonPayload.fromMap(input.params(), {{inputSimpleName}}.class)", Map.of("inputSimpleName", simpleName(spec.inputType())));

    String outputConversion = outputIsRuntime ? "out" : "new EventOutput(JsonPayload.toMap(out))";

    String jsonPayloadImport =
        (inputIsRuntime && outputIsRuntime) ? "" : "import cbs.dsl.api.JsonPayload;\n";
    String inputTypeImport =
        inputIsRuntime ? "" : Substitutor.format("import {{inputType}};\n", Map.of("inputType", spec.inputType()));
    String outputTypeImport =
        outputIsRuntime ? "" : Substitutor.format("import {{outputType}};\n", Map.of("outputType", spec.outputType()));

    String dslBodyOrFallback = (spec.dslBody() != null && !spec.dslBody().isBlank())
        ? spec.dslBody()
        : "return EventDsl.event(\"" + spec.code() + "\").build();";
    String dslImportsBlock = (spec.dslImports() != null && !spec.dslImports().isBlank())
        ? spec.dslImports().trim() + "\n"
        : ((spec.dslBody() == null || spec.dslBody().isBlank()) ? "import cbs.dsl.builder.EventDsl;\n" : "");

    String sourceTemplate = //language=java
        """
        package {{definitionsPackage}};
        
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.DslObject;
        import cbs.dsl.api.EventDefinition;
        import cbs.dsl.api.EventTypes.EventInput;
        import cbs.dsl.api.EventTypes.EventOutput;
        import cbs.dsl.api.ParameterDefinition;
        import cbs.dsl.api.context.DisplayScope;
        import cbs.dsl.api.context.EnrichmentContext;
        import cbs.dsl.api.context.FinishContext;
        import cbs.dsl.api.context.TransactionsScope;
        import cbs.nova.model.EventWorkflowRequest;
        import cbs.nova.model.WorkflowExecutionResponse;
        import io.temporal.workflow.WorkflowInterface;
        import io.temporal.workflow.WorkflowMethod;
        import {{generatedPackage}}.{{workflowInterfaceName}};
        {{jsonPayloadImport}}        import {{specPackageName}}.{{specClassName}};
        {{inputTypeImport}}{{outputTypeImport}}
        {{dslImportsBlock}}        import java.util.Collections;
        import java.util.List;
        import java.util.function.BiConsumer;
        import java.util.function.Consumer;
        
        /**
         * Generated EventDefinition wrapper + Workflow implementation for {{specClassName}}.
         * <strong>WARNING:</strong> Auto-generated — do not edit.
         */
        @javax.annotation.processing.Generated(
            value = "cbs.dsl.codegen.EventCodeGenerator",
            date = "{{timestamp}}"
        )
        public class {{wrapperClassName}} implements EventDefinition, {{workflowInterfaceName}} {
        
            private final {{specClassName}} function;
            private final EventWorkflowOrchestrator orchestrator;
        
            public {{wrapperClassName}}() {
                this(null, null);
            }
        
            public {{wrapperClassName}}(DslComponentResolver resolver) {
                this(resolver, null);
            }
        
            public {{wrapperClassName}}(EventWorkflowOrchestrator orchestrator) {
                this(null, orchestrator);
            }
        
            public {{wrapperClassName}}(DslComponentResolver resolver, EventWorkflowOrchestrator orchestrator) {
                this.function = resolver != null ? resolver.resolve({{specClassName}}.class) : new {{specClassName}}();
                this.orchestrator = orchestrator;
            }
        
            @Override
            public String getCode() {
                return "{{specCode}}";
            }
        
            @Override
            public List<ParameterDefinition> getParameters() {
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
                {{inputSimpleName}} typed = {{inputConversion}};
                {{outputSimpleName}} out = function.preview(typed);
                return {{outputConversion}};
            }
        
            @Override
            public EventOutput execute(EventInput input) {
                {{inputSimpleName}} typed = {{inputConversion}};
                {{outputSimpleName}} out = function.execute(typed);
                return {{outputConversion}};
            }
        
            @Override
            public DslObject dsl() {
                {{dslBodyOrFallback}}
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

    Map<String, String> params = new HashMap<>();
    params.put("definitionsPackage", DEFINITIONS_PACKAGE);
    params.put("generatedPackage", GENERATED_PACKAGE);
    params.put("workflowInterfaceName", workflowInterfaceName);
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
    params.put("dslImportsBlock", dslImportsBlock);
    params.put("dslBodyOrFallback", dslBodyOrFallback);
    String source = Substitutor.format(sourceTemplate, params);

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
