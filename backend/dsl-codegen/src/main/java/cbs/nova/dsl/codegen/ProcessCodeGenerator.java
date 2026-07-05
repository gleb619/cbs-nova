package cbs.nova.dsl.codegen;

import cbs.nova.dsl.ProcessDescriptor;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class ProcessCodeGenerator {
  private static final String BASE_PACKAGE = "cbs.nova.dsl.generated";

  public @NonNull List<GeneratedSource> generate(@NonNull ProcessDescriptor descriptor) {
    String name = descriptor.name();
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";

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
            + "import io.temporal.workflow.WorkflowInterface;\n"
            + "import io.temporal.workflow.WorkflowMethod;\n\n"
            + "@WorkflowInterface\n"
            + "public interface "
            + interfaceName
            + " {\n"
            + "  @WorkflowMethod\n"
            + "  Object run(Object input);\n"
            + "}\n";
  }

  private String generateImpl(String pkg, String processName, String interfaceName,
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
            + "  public Object run(Object input) {\n"
            + "    var ctx = SimpleContext.of(input, ExecutionMode.RUN);\n"
            + "    var result = GlobalManager.getInstance().runProcess(\""
            + processName
            + "\", ctx);\n"
            + "    if (!result.isSuccess()) throw new RuntimeException(\"Process failed\", result.cause());\n"
            + "    return result.value();\n"
            + "  }\n"
            + "}\n";
  }
}
