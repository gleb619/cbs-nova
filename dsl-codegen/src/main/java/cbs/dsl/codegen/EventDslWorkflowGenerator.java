package cbs.dsl.codegen;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates Temporal Workflow + Activity classes for DSL-defined Events.
 *
 * <p>Produces four artifacts per DSL event:
 *
 * <ol>
 *   <li>{@code {Code}EventWorkflow} — Temporal {@code @WorkflowInterface}
 *   <li>{@code {Code}EventWorkflowImpl} — workflow implementation that calls prepareContext
 *       activity then iterates through transaction activities
 *   <li>{@code {Code}EventActivity} — Temporal {@code @ActivityInterface} with prepareContext
 *   <li>{@code {Code}EventActivityImpl} — activity implementation (stub; context execution TODO)
 * </ol>
 */
public class EventDslWorkflowGenerator {

  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";

  private final Filer filer;

  public EventDslWorkflowGenerator() {
    this(null);
  }

  public EventDslWorkflowGenerator(Filer filer) {
    this.filer = filer;
  }

  public void generate(List<EventWorkflowSpec> specs) throws IOException {
    for (EventWorkflowSpec spec : specs) {
      generateWorkflowInterface(spec);
      generateWorkflowImpl(spec);
      generateActivityInterface(spec);
      generateActivityImpl(spec);
    }
  }

  public void generateToPath(List<EventWorkflowSpec> specs, Path outputDir) throws IOException {
    for (EventWorkflowSpec spec : specs) {
      String wfInterface = generateWorkflowInterfaceCode(spec);
      writeWorkflowInterfaceToPath(spec, wfInterface, outputDir);

      String wfImpl = generateWorkflowImplCode(spec);
      writeWorkflowImplToPath(spec, wfImpl, outputDir);

      String actInterface = generateActivityInterfaceCode(spec);
      writeActivityInterfaceToPath(spec, actInterface, outputDir);

      String actImpl = generateActivityImplCode(spec);
      writeActivityImplToPath(spec, actImpl, outputDir);
    }
  }

  public List<GeneratedFile> generateFileSpecs(List<EventWorkflowSpec> specs, Path outputDir) {
    List<GeneratedFile> files = new ArrayList<>();
    for (EventWorkflowSpec spec : specs) {
      files.add(new GeneratedFile(
          outputDir
              .resolve("cbs/dsl/codegen/generated")
              .resolve(toClassName(spec.eventCode()) + "EventWorkflow.java"),
          generateWorkflowInterfaceCode(spec)));
      files.add(new GeneratedFile(
          outputDir
              .resolve("cbs/dsl/codegen/generated")
              .resolve(toClassName(spec.eventCode()) + "EventWorkflowImpl.java"),
          generateWorkflowImplCode(spec)));
      files.add(new GeneratedFile(
          outputDir
              .resolve("cbs/dsl/codegen/generated")
              .resolve(toClassName(spec.eventCode()) + "EventActivity.java"),
          generateActivityInterfaceCode(spec)));
      files.add(new GeneratedFile(
          outputDir
              .resolve("cbs/dsl/codegen/generated")
              .resolve(toClassName(spec.eventCode()) + "EventActivityImpl.java"),
          generateActivityImplCode(spec)));
    }
    return files;
  }

  public record GeneratedFile(Path path, String content) {}

  public void generateWorkflowInterface(EventWorkflowSpec spec) throws IOException {
    String source = generateWorkflowInterfaceCode(spec);
    writeWorkflowInterface(spec, source);
  }

  public String generateWorkflowInterfaceCode(EventWorkflowSpec spec) {
    String className = toClassName(spec.eventCode()) + "EventWorkflow";
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    String sourceTemplate = // language=java
        """
        package {{package}};

        import cbs.nova.model.EventWorkflowRequest;
        import cbs.nova.model.WorkflowExecutionResponse;
        import io.temporal.workflow.WorkflowInterface;
        import io.temporal.workflow.WorkflowMethod;
        import javax.annotation.processing.Generated;

        @Generated(
            value = "cbs.dsl.codegen.EventDslWorkflowGenerator",
            date = "{{timestamp}}"
        )
        @WorkflowInterface
        public interface {{className}} {

            @WorkflowMethod(name = "{{workflowMethodName}}")
            WorkflowExecutionResponse execute(EventWorkflowRequest input);
        }
        """;

    return Substitutor.format(
        sourceTemplate,
        Map.of(
            "package", GENERATED_PACKAGE,
            "timestamp", timestamp,
            "className", className,
            "workflowMethodName", spec.eventCode()));
  }

  public void writeWorkflowInterface(EventWorkflowSpec spec, String source) throws IOException {
    String className = toClassName(spec.eventCode()) + "EventWorkflow";
    String fqcn = GENERATED_PACKAGE + "." + className;
    JavaFileObject file = filer.createSourceFile(fqcn);
    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  public void writeWorkflowInterfaceToPath(EventWorkflowSpec spec, String source, Path outputDir)
      throws IOException {
    String className = toClassName(spec.eventCode()) + "EventWorkflow";
    Path outputPath = outputDir.resolve("cbs/dsl/codegen/generated").resolve(className + ".java");
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, source);
  }

  public void generateActivityInterface(EventWorkflowSpec spec) throws IOException {
    String source = generateActivityInterfaceCode(spec);
    writeActivityInterface(spec, source);
  }

  public String generateActivityInterfaceCode(EventWorkflowSpec spec) {
    String className = toClassName(spec.eventCode()) + "EventActivity";
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    String sourceTemplate = // language=java
        """
        package {{package}};

        import cbs.nova.model.EventWorkflowRequest;
        import io.temporal.activity.ActivityInterface;
        import io.temporal.activity.ActivityMethod;
        import java.util.Map;
        import javax.annotation.processing.Generated;

        @Generated(
            value = "cbs.dsl.codegen.EventDslWorkflowGenerator",
            date = "{{timestamp}}"
        )
        @ActivityInterface
        public interface {{className}} {

            @ActivityMethod
            Map<String, Object> prepareContext(EventWorkflowRequest input);
        }
        """;

    return Substitutor.format(
        sourceTemplate,
        Map.of(
            "package", GENERATED_PACKAGE,
            "timestamp", timestamp,
            "className", className));
  }

  public void writeActivityInterface(EventWorkflowSpec spec, String source) throws IOException {
    String className = toClassName(spec.eventCode()) + "EventActivity";
    String fqcn = GENERATED_PACKAGE + "." + className;
    JavaFileObject file = filer.createSourceFile(fqcn);
    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  public void writeActivityInterfaceToPath(EventWorkflowSpec spec, String source, Path outputDir)
      throws IOException {
    String className = toClassName(spec.eventCode()) + "EventActivity";
    Path outputPath = outputDir.resolve("cbs/dsl/codegen/generated").resolve(className + ".java");
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, source);
  }

  public void generateActivityImpl(EventWorkflowSpec spec) throws IOException {
    String source = generateActivityImplCode(spec);
    writeActivityImpl(spec, source);
  }

  public String generateActivityImplCode(EventWorkflowSpec spec) {
    String activityClassName = toClassName(spec.eventCode()) + "EventActivity";
    String implClassName = activityClassName + "Impl";
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    String sourceTemplate = // language=java
        """
        package {{package}};

        import cbs.nova.model.EventWorkflowRequest;
        import java.util.Map;
        import javax.annotation.processing.Generated;

        @Generated(
            value = "cbs.dsl.codegen.EventDslWorkflowGenerator",
            date = "{{timestamp}}"
        )
        public class {{implClassName}} implements {{activityClassName}} {

            @Override
            public Map<String, Object> prepareContext(EventWorkflowRequest input) {
                // TODO: execute event's context{} DSL block via DslRegistry
                return Map.of();
            }
        }
        """;

    return Substitutor.format(
        sourceTemplate,
        Map.of(
            "package", GENERATED_PACKAGE,
            "timestamp", timestamp,
            "implClassName", implClassName,
            "activityClassName", activityClassName));
  }

  public void writeActivityImpl(EventWorkflowSpec spec, String source) throws IOException {
    String className = toClassName(spec.eventCode()) + "EventActivityImpl";
    String fqcn = GENERATED_PACKAGE + "." + className;
    JavaFileObject file = filer.createSourceFile(fqcn);
    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  public void writeActivityImplToPath(EventWorkflowSpec spec, String source, Path outputDir)
      throws IOException {
    String className = toClassName(spec.eventCode()) + "EventActivityImpl";
    Path outputPath = outputDir.resolve("cbs/dsl/codegen/generated").resolve(className + ".java");
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, source);
  }

  public void generateWorkflowImpl(EventWorkflowSpec spec) throws IOException {
    String source = generateWorkflowImplCode(spec);
    writeWorkflowImpl(spec, source);
  }

  public String generateWorkflowImplCode(EventWorkflowSpec spec) {
    String workflowClassName = toClassName(spec.eventCode()) + "EventWorkflow";
    String activityClassName = toClassName(spec.eventCode()) + "EventActivity";
    String implClassName = workflowClassName + "Impl";
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

    String txActivityFields = spec.transactionCodes().stream()
        .map(tx -> {
          String txActivityName = toClassName(tx) + "TransactionActivity";
          String fieldName = toFieldName(tx) + "Activity";
          return """
                    private final {{txActivityName}} {{fieldName}} = Workflow.newActivityStub(
                        {{txActivityName}}.class,
                        ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(30))
                            .build());""";
        })
        .collect(Collectors.joining("\n\n"));

    String txExecutionCalls = spec.transactionCodes().stream()
        .map(tx -> {
          String txActivityName = toClassName(tx) + "TransactionActivity";
          String fieldName = toFieldName(tx) + "Activity";
          return """
                    // Execute transaction {{txCode}}
                    TransactionInput {{fieldName}}Input = new TransactionInput(
                        context, input.eventCode());
                    {{fieldName}}.execute({{fieldName}}Input);""";
        })
        .collect(Collectors.joining("\n"));

    String sourceTemplate = // language=java
        """
        package {{package}};

        import cbs.dsl.api.TransactionTypes.TransactionInput;
        import cbs.nova.model.EventWorkflowRequest;
        import cbs.nova.model.WorkflowExecutionResponse;
        import io.temporal.activity.ActivityOptions;
        import io.temporal.workflow.Workflow;
        import java.time.Duration;
        import java.util.Map;
        import javax.annotation.processing.Generated;

        @Generated(
            value = "cbs.dsl.codegen.EventDslWorkflowGenerator",
            date = "{{timestamp}}"
        )
        public class {{implClassName}} implements {{workflowClassName}} {

            private final {{activityClassName}} activity = Workflow.newActivityStub(
                {{activityClassName}}.class,
                ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .build());

        {{txActivityFields}}

            @Override
            public WorkflowExecutionResponse execute(EventWorkflowRequest input) {
                // 1. Prepare context via event activity
                Map<String, Object> context = activity.prepareContext(input);

                // 2. Execute transactions sequentially
        {{txExecutionCalls}}

                return new WorkflowExecutionResponse(null, "COMPLETED");
            }
        }
        """;

    return Substitutor.format(
        sourceTemplate,
        Map.of(
            "package", GENERATED_PACKAGE,
            "timestamp", timestamp,
            "implClassName", implClassName,
            "workflowClassName", workflowClassName,
            "activityClassName", activityClassName,
            "txActivityFields", txActivityFields,
            "txExecutionCalls", txExecutionCalls));
  }

  public void writeWorkflowImpl(EventWorkflowSpec spec, String source) throws IOException {
    String className = toClassName(spec.eventCode()) + "EventWorkflowImpl";
    String fqcn = GENERATED_PACKAGE + "." + className;
    JavaFileObject file = filer.createSourceFile(fqcn);
    try (PrintWriter writer = new PrintWriter(file.openWriter())) {
      writer.print(source);
    }
  }

  public void writeWorkflowImplToPath(EventWorkflowSpec spec, String source, Path outputDir)
      throws IOException {
    String className = toClassName(spec.eventCode()) + "EventWorkflowImpl";
    Path outputPath = outputDir.resolve("cbs/dsl/codegen/generated").resolve(className + ".java");
    Files.createDirectories(outputPath.getParent());
    Files.writeString(outputPath, source);
  }

  static String toClassName(String code) {
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

  private static String toFieldName(String code) {
    String className = toClassName(code);
    return Character.toLowerCase(className.charAt(0)) + className.substring(1);
  }
}
