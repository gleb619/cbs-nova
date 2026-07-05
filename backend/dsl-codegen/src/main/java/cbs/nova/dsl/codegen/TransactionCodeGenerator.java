package cbs.nova.dsl.codegen;

import cbs.nova.dsl.TransactionDescriptor;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class TransactionCodeGenerator {
  private static final String BASE_PACKAGE = "cbs.nova.dsl.generated";

  public @NonNull List<GeneratedSource> generate(@NonNull TransactionDescriptor descriptor) {
    String name = descriptor.name();
    String interfaceName = name + "TransactionActivity";
    String implName = name + "TransactionDefinition";

    String interfaceSource = generateInterface(BASE_PACKAGE, interfaceName);
    String implSource = generateImpl(BASE_PACKAGE, name, interfaceName, implName);

    return List.of(
            new GeneratedSource(BASE_PACKAGE, interfaceName, interfaceSource),
            new GeneratedSource(BASE_PACKAGE, implName, implSource));
  }

  private String generateInterface(String pkg, String interfaceName) {
    return "package "
            + pkg
            + ";\n\n"
            + "import io.temporal.activity.ActivityInterface;\n"
            + "import io.temporal.activity.ActivityMethod;\n\n"
            + "@ActivityInterface\n"
            + "public interface "
            + interfaceName
            + " {\n"
            + "  @ActivityMethod\n"
            + "  Object execute(Object input);\n"
            + "}\n";
  }

  private String generateImpl(String pkg, String transactionName, String interfaceName,
          String implName) {
    return "package "
            + pkg
            + ";\n\n"
            + "import cbs.nova.dsl.ExecutionMode;\n"
            + "import cbs.nova.dsl.GlobalManager;\n"
            + "import cbs.nova.dsl.SimpleContext;\n\n"
            + "public class "
            + implName
            + " implements "
            + interfaceName
            + " {\n"
            + "  @Override\n"
            + "  public Object execute(Object input) {\n"
            + "    var ctx = SimpleContext.of(input, ExecutionMode.RUN);\n"
            + "    var result = GlobalManager.getInstance().runTransaction(\""
            + transactionName
            + "\", ctx);\n"
            + "    if (!result.isSuccess()) throw new RuntimeException(\"Transaction failed\", result.cause());\n"
            + "    return result.value();\n"
            + "  }\n"
            + "}\n";
  }
}
