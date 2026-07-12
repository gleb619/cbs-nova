package cbs.nova.dsl.codegen;

import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.utils.Substitutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;

public final class ProcessCodeGenerator {

  private static final String BASE_PACKAGE = "cbs.nova.dsl.generated";

  static String versionedPackage(String name, String version) {
    String nameSegment = name.toLowerCase().replaceAll("[^a-z0-9]", "");
    String versionSegment = version.replaceAll("[^a-z0-9]", "");
    if (!versionSegment.isEmpty() && Character.isDigit(versionSegment.charAt(0))) {
      versionSegment = "v" + versionSegment;
    }
    return BASE_PACKAGE + "." + nameSegment + "." + versionSegment;
  }

  public @NonNull List<GeneratedSource> generate(@NonNull ProcessDescriptor descriptor) {
    return generate(descriptor, null);
  }

  public @NonNull List<GeneratedSource> generate(
          @NonNull ProcessDescriptor descriptor,
          String buildVersion) {
    String name = descriptor.name();
    String pkg = versionedPackage(descriptor.name(), descriptor.version());
    String versionConstant = resolveVersion(descriptor.version(), buildVersion);
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";

    return List.of(
            new GeneratedSource(pkg, interfaceName,
                    generateInterface(pkg, interfaceName, descriptor)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            versionConstant, descriptor.taskQueue(),
                            descriptor.inputType(), descriptor.outputType(),
                            descriptor.hasCompensation())));
  }

  private static @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private String generateInterface(String pkg, String interfaceName, ProcessDescriptor descriptor) {
    String inputType = typeName(descriptor.inputType());
    String outputType = typeName(descriptor.outputType());
    List<String> imports = new ArrayList<>();
    addImport(imports, descriptor.inputType());
    addImport(imports, descriptor.outputType());

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";

    return Substitutor.format(
            """
                    package ${pkg};${importBlock}
                    import io.temporal.workflow.QueryMethod;
                    import io.temporal.workflow.WorkflowInterface;
                    import io.temporal.workflow.WorkflowMethod;

                    @WorkflowInterface
                    public interface ${interfaceName} {
                      @QueryMethod
                      String getVersion();

                      @WorkflowMethod
                      ${outputType} run(${inputType} input);
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "importBlock", importBlock,
                    "interfaceName", interfaceName,
                    "outputType", outputType,
                    "inputType", inputType));
  }

  private String generateImpl(
          String pkg, String processName, String interfaceName, String implName,
          String versionConstant, String taskQueue, Class<?> inputType,
          Class<?> outputType, boolean hasCompensation) {
    String inputTypeName = typeName(inputType);
    String outputTypeName = typeName(outputType);
    List<String> imports = new ArrayList<>();
    addImport(imports, inputType);
    addImport(imports, outputType);

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";

    String compensationBody = hasCompensation
            ? "() ->\n            GlobalManager.getInstance().runProcess(\"" + processName
                    + "-compensation\", compensationCtx)"
            : "() -> { /* default no-op compensation */ }";

    String template = """
            package ${pkg};${importBlock}
            import cbs.nova.dsl.ExecutionMode;
            import cbs.nova.dsl.GlobalManager;
            import cbs.nova.dsl.SimpleContext;
            import io.temporal.workflow.Saga;
            import io.temporal.workflow.Workflow;
            import java.util.Map;

            public class ${implName} implements ${interfaceName} {
              private static final String VERSION = "${version}";

              private static final String TASK_QUEUE = "${taskQueue}";

              @Override
              public String getVersion() {
                return VERSION;
              }

              @Override
              public ${outputTypeName} run(${inputTypeName} input) {
                Saga saga = new Saga(new Saga.Options.Builder().build());
                String runId = Workflow.getInfo().getRunId();
                var ctx = new SimpleContext<>(input, Map.of(), ExecutionMode.RUN, runId);
                var compensationCtx = new SimpleContext<>(input, Map.of(), ExecutionMode.RUN, runId);
                saga.addCompensation(${compensationBody});
                try {
                  var result = GlobalManager.getInstance().runProcess("${processName}", ctx);
                  if (!result.isSuccess()) {
                    saga.compensate();
                    throw new RuntimeException("Process failed", result.cause());
                  }
                  return ${castIfNeeded}result.value();
                } catch (Exception e) {
                  saga.compensate();
                  throw e;
                }
              }
            }
            """;

    return Substitutor.format(
            template,
            Map.ofEntries(
                    Map.entry("pkg", pkg),
                    Map.entry("importBlock", importBlock),
                    Map.entry("processName", processName),
                    Map.entry("interfaceName", interfaceName),
                    Map.entry("implName", implName),
                    Map.entry("version", versionConstant),
                    Map.entry("taskQueue", taskQueue),
                    Map.entry("outputTypeName", outputTypeName),
                    Map.entry("inputTypeName", inputTypeName),
                    Map.entry("castIfNeeded", castIfNeeded(outputType)),
                    Map.entry("compensationBody", compensationBody)));
  }

  private String typeName(Class<?> type) {
    return type == null ? "Object" : type.getSimpleName();
  }

  private String castIfNeeded(Class<?> type) {
    return type == null ? "" : "(" + type.getSimpleName() + ") ";
  }

  private void addImport(List<String> imports, Class<?> type) {
    if (type == null || type.getPackageName().startsWith("java.lang")) {
      return;
    }
    imports.add("import " + type.getCanonicalName() + ";");
  }
}
