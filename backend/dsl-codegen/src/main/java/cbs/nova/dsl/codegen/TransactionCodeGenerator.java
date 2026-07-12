package cbs.nova.dsl.codegen;

import cbs.nova.dsl.transaction.TransactionDescriptor;
import cbs.nova.dsl.utils.Substitutor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

public final class TransactionCodeGenerator {

  public @NonNull List<GeneratedSource> generate(@NonNull TransactionDescriptor descriptor) {
    return generate(descriptor, null);
  }

  public @NonNull List<GeneratedSource> generate(
          @NonNull TransactionDescriptor descriptor,
          String buildVersion) {
    String name = descriptor.name();
    String pkg = ProcessCodeGenerator.versionedPackage(descriptor.name(), descriptor.version());
    String versionConstant = resolveVersion(descriptor.version(), buildVersion);
    String interfaceName = name + "TransactionActivity";
    String implName = name + "TransactionDefinition";

    return List.of(
            new GeneratedSource(pkg, interfaceName, generateInterface(pkg, interfaceName)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            versionConstant, descriptor.taskQueue())));
  }

  private static @NonNull String resolveVersion(
          @NonNull String descriptorVersion,
          String buildVersion) {
    return (buildVersion != null && !buildVersion.isBlank()) ? buildVersion : descriptorVersion;
  }

  private String generateInterface(String pkg, String interfaceName) {
    return Substitutor.format(
            """
                    package ${pkg};

                    import io.temporal.activity.ActivityInterface;
                    import io.temporal.activity.ActivityMethod;

                    @ActivityInterface
                    public interface ${interfaceName} {
                      @ActivityMethod
                      String getVersion();

                      @ActivityMethod
                      Object execute(Object input);
                    }
                    """,
            Map.of(
                    "pkg", pkg,
                    "interfaceName", interfaceName));
  }

  private String generateImpl(String pkg, String transactionName, String interfaceName,
          String implName, String versionConstant, String taskQueue) {
    return Substitutor.format(
            """
                    package ${pkg};

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
                      public Object execute(Object input) {
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
                    "transactionName", transactionName,
                    "interfaceName", interfaceName,
                    "implName", implName,
                    "version", versionConstant,
                    "taskQueue", taskQueue));
  }
}
