package cbs.nova.dsl.codegen;

import cbs.nova.dsl.DslTemporalTransactionRequest;
import cbs.nova.dsl.GeneratedTransactionActivity;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.utils.Substitutor;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class TransactionCodeGenerator {

  private final CodegenNaming codegenNaming;

  public @NonNull List<GeneratedSource> generate(
          @NonNull TransactionDescriptor descriptor,
          @Nullable String buildVersion,
          @Nullable String targetPackage) {
    String name = descriptor.name();
    String versionConstant = resolveVersion(descriptor.version(), buildVersion);
    String pkg = codegenNaming.versionedPackage(descriptor.name(), versionConstant,
            targetPackage);
    String interfaceName = name + "TransactionActivity";
    String implName = name + "TransactionDefinition";
    String inputTypeName = typeName(descriptor.inputType());
    boolean hasCompensation = descriptor.hasCompensation();

    return List.of(
            new GeneratedSource(pkg, interfaceName,
                    generateInterface(pkg, name, interfaceName, inputTypeName,
                            importLine(descriptor.inputType()), hasCompensation)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            versionConstant, descriptor.taskQueue(), inputTypeName,
                            importLine(descriptor.inputType()), hasCompensation)));
  }

  private static @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private String generateInterface(String pkg, String transactionName, String interfaceName,
          String inputTypeName, String inputImport, boolean hasCompensation) {
    String importBlock = buildImportBlock(inputImport,
            "import " + DslTemporalTransactionRequest.class.getCanonicalName() + ";");
    String compensationMethod = hasCompensation
            ? "\n  @ActivityMethod\n  void compensate(DslTemporalTransactionRequest<"
                    + inputTypeName
                    + "> request, Throwable error);\n"
            : "";
    return Substitutor.format(
            """
                    package ${pkg};${importBlock}
                    import cbs.nova.dsl.GeneratedTransactionActivity;
                    import io.temporal.activity.ActivityInterface;
                    import io.temporal.activity.ActivityMethod;

                    @ActivityInterface(namePrefix = "${transactionName}_")
                    public interface ${interfaceName} extends GeneratedTransactionActivity {

                      @ActivityMethod
                      String getVersion();

                      @ActivityMethod
                      Object execute(DslTemporalTransactionRequest<${inputTypeName}> request);${compensationMethod}
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "importBlock", importBlock,
                    "interfaceName", interfaceName,
                    "transactionName", transactionName,
                    "inputTypeName", inputTypeName,
                    "compensationMethod", compensationMethod));
  }

  private String generateImpl(String pkg, String transactionName, String interfaceName,
          String implName, String versionConstant, String taskQueue, String inputTypeName,
          String inputImport, boolean hasCompensation) {
    String importBlock = buildImportBlock(inputImport,
            "import " + DslTemporalTransactionRequest.class.getCanonicalName() + ";");
    String compensationMethod = hasCompensation
            ? Substitutor.format(
                    """

                            public void compensate(DslTemporalTransactionRequest<${inputTypeName}> request, Throwable error) {
                              String runId = request.runId();
                              ${inputTypeName} input = request.payload();
                              var ctx = GlobalManager.getInstance()
                                      .createContext(input, Map.of(), ExecutionMode.COMPENSATION, runId);
                              GlobalManager.getInstance().compensateTransaction("${transactionName}", ctx, error);
                            }
                            """,
                    Map.of("inputTypeName", inputTypeName, "transactionName", transactionName))
            : "";
    return Substitutor.format(
            """
                    package ${pkg};${importBlock}
                    import cbs.nova.dsl.DslTemporalTransactionRequest;
                    import cbs.nova.dsl.ExecutionMode;
                    import cbs.nova.dsl.GlobalManager;
                    import cbs.nova.dsl.Result;
                    import cbs.nova.dsl.TransactionRouting;
                    import java.util.Map;

                    public class ${implName} implements ${interfaceName} {
                      private static final String VERSION = "${version}";

                      private static final String TASK_QUEUE = "${taskQueue}";

                      @Override
                      public String getVersion() {
                        return VERSION;
                      }

                      @Override
                      public Object execute(DslTemporalTransactionRequest<${inputTypeName}> request) {
                        String runId = request.runId();
                        ${inputTypeName} input = request.payload();
                        var ctx = GlobalManager.getInstance()
                                .createContext(input, Map.of(), ExecutionMode.RUN, runId)
                                .withTransactionRouting(TransactionRouting.TEMPORAL_ACTIVITY);
                        var result = GlobalManager.getInstance().runTransaction("${transactionName}", ctx);
                        if (!result.isSuccess()) throw new RuntimeException("Transaction failed", result.cause());
                        return result.value();
                      }${compensationMethod}
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "importBlock", importBlock,
                    "transactionName", transactionName,
                    "interfaceName", interfaceName,
                    "implName", implName,
                    "version", versionConstant,
                    "taskQueue", taskQueue,
                    "inputTypeName", inputTypeName,
                    "compensationMethod", compensationMethod));
  }

  private String buildImportBlock(String inputImport, String requestImport) {
    List<String> imports = new ArrayList<>();
    if (!inputImport.isEmpty()) {
      imports.add(inputImport);
    }
    imports.add(requestImport);
    return imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";
  }

  private String typeName(Class<?> type) {
    return type == null ? "Object" : type.getSimpleName();
  }

  private String importLine(Class<?> type) {
    if (type == null || type.getPackageName().startsWith("java.lang")) {
      return "";
    }
    return "import " + type.getCanonicalName() + ";";
  }
}
