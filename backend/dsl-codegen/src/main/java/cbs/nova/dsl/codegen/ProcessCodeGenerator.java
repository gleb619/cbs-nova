package cbs.nova.dsl.codegen;

import cbs.nova.dsl.ProcessDescriptor;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class ProcessCodeGenerator {
  private static final String BASE_PACKAGE = "cbs.nova.dsl.generated";

  public @NonNull List<GeneratedSource> generate(@NonNull ProcessDescriptor descriptor) {
    String name = descriptor.name();
    String pkg = versionedPackage(descriptor.name(), descriptor.version());
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";

    return List.of(
            new GeneratedSource(pkg, interfaceName, generateInterface(pkg, interfaceName)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            descriptor.version(), descriptor.taskQueue(),
                            descriptor.hasCompensation())));
  }

  static String versionedPackage(String name, String version) {
    String nameSegment = name.toLowerCase().replaceAll("[^a-z0-9]", "");
    String versionSegment = version.replaceAll("[^a-z0-9]", "");
    return BASE_PACKAGE + "." + nameSegment + "." + versionSegment;
  }

  private String generateInterface(String pkg, String interfaceName) {
    return "package "
            + pkg
            + ";\n\n"
            + "import io.temporal.workflow.QueryMethod;\n"
            + "import io.temporal.workflow.WorkflowInterface;\n"
            + "import io.temporal.workflow.WorkflowMethod;\n\n"
            + "@WorkflowInterface\n"
            + "public interface "
            + interfaceName
            + " {\n"
            + "  @QueryMethod\n"
            + "  String getVersion();\n\n"
            + "  @WorkflowMethod\n"
            + "  Object run(Object input);\n"
            + "}\n";
  }

  private String generateImpl(
          String pkg, String processName, String interfaceName, String implName, String version,
          String taskQueue, boolean hasCompensation) {
    if (hasCompensation) {
      return "package "
              + pkg
              + ";\n\n"
              + "import cbs.nova.dsl.ExecutionMode;\n"
              + "import cbs.nova.dsl.GlobalManager;\n"
              + "import cbs.nova.dsl.SimpleContext;\n"
              + "import io.temporal.workflow.Saga;\n\n"
              + "public class "
              + implName
              + " implements "
              + interfaceName
              + " {\n"
              + "  private static final String VERSION = \""
              + version
              + "\";\n\n"
              + "  private static final String TASK_QUEUE = \""
              + taskQueue
              + "\";\n\n"
              + "  @Override\n"
              + "  public String getVersion() {\n"
              + "    return VERSION;\n"
              + "  }\n\n"
              + "  @Override\n"
              + "  public Object run(Object input) {\n"
              + "    Saga saga = new Saga(new Saga.Options.Builder().build());\n"
              + "    var ctx = SimpleContext.of(input, ExecutionMode.RUN);\n"
              + "    var compensationCtx = SimpleContext.of(input, ExecutionMode.RUN);\n"
              + "    saga.addCompensation(\n"
              + "        () ->\n"
              + "            GlobalManager.getInstance().runProcess(\""
              + processName
              + "-compensation\", compensationCtx));\n"
              + "    try {\n"
              + "      var result = GlobalManager.getInstance().runProcess(\""
              + processName
              + "\", ctx);\n"
              + "      if (!result.isSuccess()) {\n"
              + "        saga.compensate();\n"
              + "        throw new RuntimeException(\"Process failed\", result.cause());\n"
              + "      }\n"
              + "      return result.value();\n"
              + "    } catch (Exception e) {\n"
              + "      saga.compensate();\n"
              + "      throw e;\n"
              + "    }\n"
              + "  }\n"
              + "}\n";
    }
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
            + "  private static final String VERSION = \""
            + version
            + "\";\n\n"
            + "  private static final String TASK_QUEUE = \""
            + taskQueue
            + "\";\n\n"
            + "  @Override\n"
            + "  public String getVersion() {\n"
            + "    return VERSION;\n"
            + "  }\n\n"
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
