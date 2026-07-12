package cbs.nova.dsl.codegen;

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
    String pkg = codegenNaming.versionedPackage(descriptor.name(), descriptor.version(),
            targetPackage);
    String versionConstant = resolveVersion(descriptor.version(), buildVersion);
    String interfaceName = name + "TransactionActivity";
    String implName = name + "TransactionDefinition";
    String inputTypeName = typeName(descriptor.inputType());

    return List.of(
            new GeneratedSource(pkg, interfaceName,
                    generateInterface(pkg, name, interfaceName, inputTypeName,
                            importLine(descriptor.inputType()))),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            versionConstant, descriptor.taskQueue(), inputTypeName,
                            importLine(descriptor.inputType()))));
  }

  private static @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private String generateInterface(String pkg, String transactionName, String interfaceName,
          String inputTypeName, String inputImport) {
    String importBlock = inputImport.isEmpty() ? "" : "\n" + inputImport + "\n";
    return Substitutor.format(
            """
                    package ${pkg};${importBlock}
                    import io.temporal.activity.ActivityInterface;
                    import io.temporal.activity.ActivityMethod;

                    @ActivityInterface(namePrefix = "${transactionName}_")
                    public interface ${interfaceName} {

                      @ActivityMethod
                      String getVersion();

                      @ActivityMethod
                      Object execute(${inputTypeName} input);
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "importBlock", importBlock,
                    "interfaceName", interfaceName,
                    "transactionName", transactionName,
                    "inputTypeName", inputTypeName));
  }

  private String generateImpl(String pkg, String transactionName, String interfaceName,
          String implName, String versionConstant, String taskQueue, String inputTypeName,
          String inputImport) {
    String importBlock = inputImport.isEmpty() ? "" : "\n" + inputImport + "\n";
    return Substitutor.format(
            """
                    package ${pkg};${importBlock}
                    import cbs.nova.dsl.ExecutionMode;
                    import cbs.nova.dsl.GlobalManager;
                    import cbs.nova.dsl.SimpleContext;
                    import java.util.Map;
                    import java.util.UUID;

                    public class ${implName} implements ${interfaceName} {
                      private static final String VERSION = "${version}";

                      private static final String TASK_QUEUE = "${taskQueue}";

                      @Override
                      public String getVersion() {
                        return VERSION;
                      }

                      @Override
                      public Object execute(${inputTypeName} input) {
                        String runId = "run-" + UUID.randomUUID();
                        var ctx = new SimpleContext<>(input, Map.of(), ExecutionMode.RUN, runId);
                        var result = GlobalManager.getInstance().runTransaction("${transactionName}", ctx);
                        if (!result.isSuccess()) throw new RuntimeException("Transaction failed", result.cause());
                        return result.value();
                      }
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
                    "inputTypeName", inputTypeName));
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
