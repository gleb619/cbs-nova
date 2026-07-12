package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslTemporalProcess;
import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class ProcessCodeGenerator {

  private final CodegenNaming codegenNaming;

  public @NonNull List<GeneratedSource> generate(
          @NonNull ProcessDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    String name = descriptor.name();
    String pkg = codegenNaming.versionedPackage(descriptor.name(), descriptor.version(),
            targetPackage);
    String versionConstant = resolveVersion(descriptor.version(), buildVersion);
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";

    return List.of(
            new GeneratedSource(pkg, interfaceName,
                    generateInterface(pkg, interfaceName, descriptor)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            versionConstant, descriptor.taskQueue(),
                            descriptor.inputType(), descriptor.hasCompensation())));
  }

  private static @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private String generateInterface(String pkg, String interfaceName, ProcessDescriptor descriptor) {
    String inputType = typeName(descriptor.inputType());
    List<String> imports = new ArrayList<>();
    addImport(imports, DslTemporalProcess.class);
    addImport(imports, DslTemporalProcessRequest.class);
    addImport(imports, descriptor.inputType());

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";

    return Substitutor.format(
            """
                    package ${pkg};${importBlock}
                    import io.temporal.workflow.QueryMethod;
                    import io.temporal.workflow.WorkflowInterface;
                    import io.temporal.workflow.WorkflowMethod;

                    @WorkflowInterface
                    public interface ${interfaceName} extends DslTemporalProcess<${inputType}> {

                      @QueryMethod
                      String getVersion();

                      @Override
                      @WorkflowMethod
                      Object execute(DslTemporalProcessRequest<${inputType}> request);
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "importBlock", importBlock,
                    "interfaceName", interfaceName,
                    "inputType", inputType));
  }

  private String generateImpl(
          String pkg, String processName, String interfaceName, String implName,
          String versionConstant, String taskQueue, Class<?> inputType, boolean hasCompensation) {
    String inputTypeName = typeName(inputType);
    List<String> imports = new ArrayList<>();
    addImport(imports, DslTemporalProcessRequest.class);
    addImport(imports, inputType);

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
            import java.util.Map;

            public class ${implName} implements ${interfaceName} {
              private static final String VERSION = "${version}";

              private static final String TASK_QUEUE = "${taskQueue}";

              @Override
              public String getVersion() {
                return VERSION;
              }

              @Override
              public Object execute(DslTemporalProcessRequest<${inputTypeName}> request) {
                Saga saga = new Saga(new Saga.Options.Builder().build());
                String runId = request.runId();
                ${inputTypeName} input = request.payload();
                var ctx = new SimpleContext<>(input, Map.of(), ExecutionMode.RUN, runId);
                var compensationCtx = new SimpleContext<>(input, Map.of(), ExecutionMode.RUN, runId);
                saga.addCompensation(${compensationBody});
                try {
                  var result = GlobalManager.getInstance().runProcess("${processName}", ctx);
                  if (!result.isSuccess()) {
                    saga.compensate();
                    throw new RuntimeException("Process failed", result.cause());
                  }
                  return result.value();
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
                    Map.entry("inputTypeName", inputTypeName),
                    Map.entry("compensationBody", compensationBody)));
  }

  private String typeName(Class<?> type) {
    return type == null ? "Object" : type.getSimpleName();
  }

  private void addImport(List<String> imports, Class<?> type) {
    if (type == null || type.getPackageName().startsWith("java.lang")) {
      return;
    }
    imports.add("import " + type.getCanonicalName() + ";");
  }
}
