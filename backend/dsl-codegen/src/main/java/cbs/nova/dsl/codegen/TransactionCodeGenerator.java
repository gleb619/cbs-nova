package cbs.nova.dsl.codegen;

import cbs.nova.dsl.TransactionDescriptor;
import org.jspecify.annotations.NonNull;

import java.text.MessageFormat;
import java.util.List;

public final class TransactionCodeGenerator {

  public @NonNull List<GeneratedSource> generate(@NonNull TransactionDescriptor descriptor) {
    String name = descriptor.name();
    String pkg = ProcessCodeGenerator.versionedPackage(descriptor.name(), descriptor.version());
    String interfaceName = name + "TransactionActivity";
    String implName = name + "TransactionDefinition";

    return List.of(
            new GeneratedSource(pkg, interfaceName, generateInterface(pkg, interfaceName)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            descriptor.version(), descriptor.taskQueue())));
  }

  private String generateInterface(String pkg, String interfaceName) {
    return MessageFormat.format(
            """
            package {0};

            import io.temporal.activity.ActivityInterface;
            import io.temporal.activity.ActivityMethod;

            @ActivityInterface
            public interface {1} '{'
              @ActivityMethod
              String getVersion();

              @ActivityMethod
              Object execute(Object input);
            '}'
            """,
            pkg, interfaceName);
  }

  private String generateImpl(String pkg, String transactionName, String interfaceName,
          String implName, String version, String taskQueue) {
    return MessageFormat.format(
            """
            package {0};

            import cbs.nova.dsl.ExecutionMode;
            import cbs.nova.dsl.GlobalManager;
            import cbs.nova.dsl.SimpleContext;

            public class {3} implements {2} '{'
              private static final String VERSION = "{5}";

              private static final String TASK_QUEUE = "{6}";

              @Override
              public String getVersion() '{'
                return VERSION;
              '}'

              @Override
              public Object execute(Object input) '{'
                var ctx = SimpleContext.of(input, ExecutionMode.RUN);
                var result = GlobalManager.getInstance().runTransaction("{1}", ctx);
                if (!result.isSuccess()) throw new RuntimeException("Transaction failed", result.cause());
                return result.value();
              '}'
            '}'
            """,
            pkg, transactionName, interfaceName, implName, version, version, taskQueue);
  }
}
