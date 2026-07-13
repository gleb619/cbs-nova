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
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public final class ProcessCodeGenerator {

  private final CodegenNaming codegenNaming;

  public @NonNull List<GeneratedSource> generate(
          @NonNull ProcessDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    String name = descriptor.name();
    String versionConstant = resolveVersion(descriptor.version(), buildVersion);
    String pkg = codegenNaming.versionedPackage(descriptor.name(), versionConstant,
            targetPackage);
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";

    return List.of(
            new GeneratedSource(pkg, interfaceName,
                    generateInterface(pkg, interfaceName, descriptor)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            versionConstant, descriptor.taskQueue(),
                            descriptor.inputType(), descriptor.hasCompensation(),
                            descriptor.transactionRefs())));
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
          String versionConstant, String taskQueue, Class<?> inputType, boolean hasCompensation,
          List<String> transactionRefs) {
    String inputTypeName = typeName(inputType);
    List<String> imports = new ArrayList<>();
    addImport(imports, DslTemporalProcessRequest.class);
    addImport(imports, inputType);

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";
    String compensationRegistrations = generateCompensationRegistrations(transactionRefs);
    String compensationMethods = generateCompensationMethods(processName, hasCompensation,
            transactionRefs);

    String template = """
            package ${pkg};${importBlock}
            import cbs.nova.dsl.CompensationRichContext;
            import cbs.nova.dsl.Context;
            import cbs.nova.dsl.DslEntityNotFoundException;
            import cbs.nova.dsl.DslTemporalProcessFailure;
            import cbs.nova.dsl.ExecutionMode;
            import cbs.nova.dsl.ExecutionTraceCollector;
            import cbs.nova.dsl.GlobalManager;
            import cbs.nova.dsl.Result;
            import cbs.nova.dsl.TransactionRouting;
            import cbs.nova.dsl.config.ContextFactory;
            import io.temporal.workflow.Saga;
            import java.util.Map;
            import java.util.concurrent.atomic.AtomicReference;

            public class ${implName} implements ${interfaceName} {

              private static final String VERSION = "${version}";

              @Override
              public String getVersion() {
                return VERSION;
              }

              @Override
              public Object execute(DslTemporalProcessRequest<${inputTypeName}> request) {
                Saga saga = new Saga(new Saga.Options.Builder().build());
                String runId = request.runId();
                ${inputTypeName} input = request.payload();
                var ctx = GlobalManager.getInstance()
                        .createContext(input, Map.of(), ExecutionMode.RUN, runId)
                        .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
                var compensationCtx = GlobalManager.getInstance()
                        .createContext(input, Map.of(), ExecutionMode.RUN, runId);
                AtomicReference<Throwable> failureRef = new AtomicReference<>();
                ${compensationRegistrations}
                try {
                  var result = GlobalManager.getInstance().runProcess("${processName}", ctx);
                  if (!result.isSuccess()) {
                    failureRef.set(result.cause());
                    saga.compensate();
                    return new DslTemporalProcessFailure("Process failed",
                            result.cause() != null ? result.cause().getMessage() : "unknown");
                  }
                  return result.value();
                } catch (Exception e) {
                  failureRef.set(e);
                  saga.compensate();
                  return new DslTemporalProcessFailure(e.getMessage(),
                          e.getClass().getName());
                }
              }

              ${compensationMethods}
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
                    Map.entry("inputTypeName", inputTypeName),
                    Map.entry("compensationRegistrations", compensationRegistrations),
                    Map.entry("compensationMethods", compensationMethods)));
  }

  private String generateCompensationRegistrations(List<String> transactionRefs) {
    StringBuilder sb = new StringBuilder();
    sb.append(
            "    saga.addCompensation(() -> compensateProcess(runId, compensationCtx, failureRef));\n");
    for (String tx : transactionRefs) {
      sb.append("    saga.addCompensation(() -> compensate")
              .append(tx)
              .append("(runId, compensationCtx, failureRef));\n");
    }
    return sb.toString();
  }

  private String generateCompensationMethods(
          String processName, boolean hasCompensation, List<String> transactionRefs) {
    StringBuilder sb = new StringBuilder();
    sb.append(generateProcessCompensationMethod(processName, hasCompensation));
    for (String tx : transactionRefs) {
      sb.append(generateTransactionCompensationMethod(tx));
    }
    return sb.toString();
  }

  private String generateProcessCompensationMethod(String processName, boolean hasCompensation) {
    String body = hasCompensation
            ? Substitutor.format(
                    "    var process = GlobalManager.getInstance().findProcess(\"${processName}\").orElseThrow(() -> new DslEntityNotFoundException(runId, \"Process not found: ${processName}\"));\n"
                            + "    if (process.compensationLogic() == null) {\n"
                            + "      return;\n"
                            + "    }\n"
                            + "    var compCtx = new CompensationRichContext<>(compensationCtx,\n"
                            + "            failureRef.get() != null ? failureRef.get() : new RuntimeException(\"compensation triggered\"),\n"
                            + "            new ExecutionTraceCollector(), new ContextFactory());\n"
                            + "    process.compensationLogic().apply(compCtx);\n",
                    Map.of("processName", processName))
            : "    /* default no-op compensation */\n";
    return "private void compensateProcess(String runId, Context<?> compensationCtx, AtomicReference<Throwable> failureRef) {\n"
            + body
            + "  }\n";
  }

  private String generateTransactionCompensationMethod(String txName) {
    return Substitutor.format(
            """
                    private void compensate${txName}(String runId, Context<?> compensationCtx, AtomicReference<Throwable> failureRef) {
                      GlobalManager.getInstance().findTransaction("${txName}").ifPresent(tx -> {
                        if (tx.compensationLogic() == null) {
                          return;
                        }
                        var compCtx = new CompensationRichContext<>(compensationCtx,
                                failureRef.get() != null ? failureRef.get() : new RuntimeException("compensation triggered"),
                                new ExecutionTraceCollector(), new ContextFactory());
                        tx.compensationLogic().apply(compCtx);
                      });
                    }
                    """,
            Map.of("txName", txName));
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
