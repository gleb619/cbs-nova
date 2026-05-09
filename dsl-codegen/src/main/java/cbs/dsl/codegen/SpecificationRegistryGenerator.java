package cbs.dsl.codegen;

import cbs.dsl.api.DslComponentResolver;
import cbs.dsl.api.SpecDefinitionRegistry;
import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Filer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates a compile-time specification registry that implements {@link SpecDefinitionRegistry}.
 *
 * <p>The produced class contains all known activity and workflow definitions baked into its
 * constructor. It can be instantiated directly or exposed as a Spring bean so that
 * {@link cbs.nova.temporal.ActivityManager} and {@link cbs.nova.temporal.WorkflowManager} can look
 * up generated artifacts without runtime classpath scanning.
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
      List<EventWorkflowModel> eventSpecs)
      throws IOException {

    if (txSpecs.isEmpty() && conditionSpecs.isEmpty() && eventSpecs.isEmpty()) {
      return;
    }

    String className = "GeneratedSpecificationRegistry";
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

        import java.util.Collections;
        import java.util.HashMap;
        import java.util.Map;
        import java.util.Set;
        import javax.annotation.processing.Generated;
        import cbs.dsl.api.DslComponentResolver;
        import cbs.dsl.api.SpecDefinitionRegistry;
        {{allImports}}

        @Generated(
            value = "cbs.dsl.codegen.SpecificationGenerator",
            date = "{{timestamp}}"
        )
        public final class {{className}} implements SpecDefinitionRegistry {

            private final Map<String, ArtifactEntry> activities = new HashMap<>();
            private final Map<String, ArtifactEntry> workflows = new HashMap<>();

            public {{className}}(DslComponentResolver resolver) {
        {{activityRegs}}
        {{workflowRegs}}
            }

            @Override
            public void registerActivity(String code, Class<?> activityInterface, Object implementation) {
                activities.put(code, new ArtifactEntry(activityInterface, implementation));
            }

            @Override
            public void registerWorkflow(String code, Class<?> workflowInterface, Object implementation) {
                workflows.put(code, new ArtifactEntry(workflowInterface, implementation));
            }

            @Override
            public Set<String> getActivityCodes() {
                return Collections.unmodifiableSet(activities.keySet());
            }

            @Override
            public Set<String> getWorkflowCodes() {
                return Collections.unmodifiableSet(workflows.keySet());
            }

            @Override
            public Class<?> getActivityInterface(String code) {
                ArtifactEntry entry = activities.get(code);
                if (entry == null) {
                    throw new IllegalArgumentException(
                        "Activity '%s' not found in %s".formatted(code, getClass().getSimpleName()));
                }
                return entry.interfaceClass();
            }

            @Override
            public Class<?> getWorkflowInterface(String code) {
                ArtifactEntry entry = workflows.get(code);
                if (entry == null) {
                    throw new IllegalArgumentException(
                        "Workflow '%s' not found in %s".formatted(code, getClass().getSimpleName()));
                }
                return entry.interfaceClass();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getActivity(String code, Class<T> activityInterface) {
                ArtifactEntry entry = activities.get(code);
                if (entry == null) {
                    throw new IllegalArgumentException(
                        "Activity '%s' not found in %s".formatted(code, getClass().getSimpleName()));
                }
                return activityInterface.cast(entry.implementation());
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getWorkflow(String code, Class<T> workflowInterface) {
                ArtifactEntry entry = workflows.get(code);
                if (entry == null) {
                    throw new IllegalArgumentException(
                        "Workflow '%s' not found in %s".formatted(code, getClass().getSimpleName()));
                }
                return workflowInterface.cast(entry.implementation());
            }

            private record ArtifactEntry(Class<?> interfaceClass, Object implementation) {}
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
    return "import " + GENERATED_PACKAGE + "." + className + "ConditionActivity;";
  }

  private static String toEventActivityInterfaceImport(String eventCode) {
    return "import " + GENERATED_PACKAGE + "." + CodeGenUtil.toClassName(eventCode) + "EventActivity;";
  }

  private static String toWorkflowInterfaceImport(String eventCode) {
    return "import " + GENERATED_PACKAGE + "." + CodeGenUtil.toClassName(eventCode) + "Workflow;";
  }

  private static String toActivityRegistration(String code, String className) {
    return "        registerActivity(\"" + code + "\", " + className + "Activity.class, new "
        + className + "Definition(resolver));";
  }

  private static String toConditionRegistration(String code, String className) {
    return "        registerActivity(\"" + code + "\", " + className
        + "ConditionActivity.class, new " + className + "Definition(resolver));";
  }

  private static String toEventActivityRegistration(String eventCode) {
    String className = CodeGenUtil.toClassName(eventCode);
    return "        registerActivity(\"" + eventCode + "\", " + className
        + "EventActivity.class, new " + className + "Definition(resolver));";
  }

  private static String toWorkflowRegistration(String eventCode) {
    String className = CodeGenUtil.toClassName(eventCode);
    return "        registerWorkflow(\"" + eventCode + "\", " + className + "Workflow.class, new "
        + className + "Definition(resolver));";
  }
}
