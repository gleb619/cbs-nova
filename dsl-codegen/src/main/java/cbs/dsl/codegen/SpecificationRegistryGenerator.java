package cbs.dsl.codegen;

import cbs.dsl.api.SpecDefinitionRegistry;
import cbs.dsl.api.SpecDefinitionRegistryProvider;
import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates a compile-time specification registry provider that implements
 * {@link SpecDefinitionRegistryProvider}.
 *
 * <p>The produced class registers all known activity and workflow definitions into the supplied
 * {@link SpecDefinitionRegistry} via its {@code register} method.
 *
 * <p>Every generated code artifact must have a {@code Definition} wrapper (even for non-Temporal
 * components). For Temporal workflows the registry uses the term <em>Specification</em>.
 */
@RequiredArgsConstructor
public class SpecificationRegistryGenerator {

  private static final String GENERATED_PACKAGE = "cbs.dsl.codegen.generated";

  private final Filer filer;

  public void generate(
      List<RegistrationModel> txSpecs,
      List<RegistrationModel> conditionSpecs,
      List<EventSpecificationModel> eventSpecs)
      throws IOException {

    if (txSpecs.isEmpty() && conditionSpecs.isEmpty() && eventSpecs.isEmpty()) {
      return;
    }

    String className = "SpecDefinitionRegistryProviderImpl";
    String fqcn = GENERATED_PACKAGE + "." + className;
    String timestamp = CodeGenUtil.currentTimestamp();

    String imports = Stream.of(
            txSpecs.stream().map(spec -> toActivityDefinitionImport(spec.className())),
            conditionSpecs.stream().map(spec -> toConditionDefinitionImport(spec.className())),
            eventSpecs.stream().map(spec -> toEventDefinitionImport(spec.eventClassName())))
        .flatMap(s -> s)
        .distinct()
        .collect(Collectors.joining("\n"));

    String activityImports = Stream.of(
            txSpecs.stream().map(spec -> toActivityInterfaceImport(spec.className())),
            conditionSpecs.stream()
                .map(spec -> toConditionActivityInterfaceImport(spec.className())),
            eventSpecs.stream().map(spec -> toEventActivityInterfaceImport(spec.eventCode())))
        .flatMap(s -> s)
        .distinct()
        .collect(Collectors.joining("\n"));

    String workflowImports = eventSpecs.stream()
        .map(spec -> toWorkflowInterfaceImport(spec.eventCode()))
        .distinct()
        .collect(Collectors.joining("\n"));

    String allImports = Stream.of(imports, activityImports, workflowImports)
        .filter(s -> !s.isBlank())
        .collect(Collectors.joining("\n"));

    String activityRegs = Stream.of(
            txSpecs.stream().map(spec -> toActivityRegistration(spec.code(), spec.className())),
            conditionSpecs.stream()
                .map(spec -> toConditionRegistration(spec.code(), spec.className())),
            eventSpecs.stream().map(spec -> toEventActivityRegistration(spec.eventCode())))
        .flatMap(s -> s)
        .collect(Collectors.joining("\n"));

    String workflowRegs = eventSpecs.stream()
        .map(spec -> toWorkflowRegistration(spec.eventCode()))
        .collect(Collectors.joining("\n"));

    String source = Substitutor.format( // language=java
        """
        package {{GENERATED_PACKAGE}};

        import javax.annotation.processing.Generated;
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.SpecDefinitionRegistry;
        import cbs.dsl.api.SpecDefinitionRegistryProvider;
        {{allImports}}

        @Generated(
            value = "cbs.dsl.codegen.SpecificationGenerator",
            date = "{{timestamp}}"
        )
        public final class {{className}} implements SpecDefinitionRegistryProvider {

            @Override
            public void register(SpecDefinitionRegistry registry, DslComponentResolver resolver) {
        {{activityRegs}}
        {{workflowRegs}}
            }
        }
        """,
        Map.ofEntries(
            Map.entry("GENERATED_PACKAGE", GENERATED_PACKAGE),
            Map.entry("allImports", allImports.isBlank() ? "" : "\n" + allImports),
            Map.entry("timestamp", timestamp),
            Map.entry("className", className),
            Map.entry(
                "activityRegs",
                activityRegs.isBlank() ? "        // No activities\n" : activityRegs + "\n"),
            Map.entry(
                "workflowRegs",
                workflowRegs.isBlank() ? "        // No workflows\n" : workflowRegs)));

    CodeGenUtil.writeToFiler(filer, fqcn, source);
    generateSpiFile(timestamp);
  }

  private void generateSpiFile(String timestamp) throws IOException {
    FileObject spiFile = filer.createResource(
        StandardLocation.CLASS_OUTPUT,
        "",
        "META-INF/services/cbs.dsl.api.SpecDefinitionRegistryProvider");
    try (PrintWriter writer = new PrintWriter(spiFile.openWriter())) {
      writer.println(Substitutor.format("""
          # Generated by SpecificationRegistryGenerator
          # Date: {{date}}
          cbs.dsl.codegen.generated.SpecDefinitionRegistryProviderImpl
          """, Map.of("date", timestamp)));
    }
  }

  private static String toActivityDefinitionImport(String className) {
    return "import " + GENERATED_PACKAGE + ".definitions." + className + "Definition;";
  }

  private static String toConditionDefinitionImport(String className) {
    return "import " + GENERATED_PACKAGE + ".definitions." + className + "Definition;";
  }

  private static String toEventDefinitionImport(String eventClassName) {
    int lastDot = eventClassName.lastIndexOf('.');
    String simpleName = lastDot >= 0 ? eventClassName.substring(lastDot + 1) : eventClassName;
    return "import " + GENERATED_PACKAGE + ".definitions." + simpleName + "Definition;";
  }

  private static String toActivityInterfaceImport(String className) {
    return "import " + GENERATED_PACKAGE + "." + className + "Activity;";
  }

  private static String toConditionActivityInterfaceImport(String className) {
    return "import " + GENERATED_PACKAGE + "." + className + "Activity;";
  }

  private static String toEventActivityInterfaceImport(String eventCode) {
    return "import " + GENERATED_PACKAGE + "." + CodeGenUtil.toClassName(eventCode)
        + "EventActivity;";
  }

  private static String toWorkflowInterfaceImport(String eventCode) {
    String className = CodeGenUtil.toClassName(eventCode);
    return "import " + GENERATED_PACKAGE + "." + className + "Workflow;";
  }

  private static String toActivityRegistration(String code, String className) {
    return "        registry.registerActivity(\"" + code + "\", " + className
        + "Activity.class, new " + className + "Definition(resolver));";
  }

  private static String toConditionRegistration(String code, String className) {
    return "        registry.registerActivity(\"" + code + "\", " + className
        + "Activity.class, new " + className + "Definition(resolver));";
  }

  private static String toEventActivityRegistration(String eventCode) {
    String className = CodeGenUtil.toClassName(eventCode);
    return "        registry.registerActivity(\"" + eventCode + "\", " + className
        + "EventActivity.class, new " + className + "Definition(resolver));";
  }

  private static String toWorkflowRegistration(String eventCode) {
    String className = CodeGenUtil.toClassName(eventCode);
    return "        registry.registerWorkflow(\"" + eventCode + "\", " + className
        + "Workflow.class, new " + className + "Definition(resolver));";
  }
}
