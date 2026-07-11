package cbs.nova.dsl.codegen;

import cbs.nova.dsl.process.ProcessDescriptor;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;

public final class ProcessCodeGenerator {
  private static final String BASE_PACKAGE = "cbs.nova.dsl.generated";

  public @NonNull List<GeneratedSource> generate(@NonNull ProcessDescriptor descriptor) {
    String name = descriptor.name();
    String pkg = versionedPackage(descriptor.name(), descriptor.version());
    String interfaceName = name + "ProcessWorkflow";
    String implName = name + "ProcessDefinition";

    return List.of(
            new GeneratedSource(pkg, interfaceName,
                    generateInterface(pkg, interfaceName, descriptor)),
            new GeneratedSource(
                    pkg, implName, generateImpl(pkg, name, interfaceName, implName,
                            descriptor.version(), descriptor.taskQueue(),
                            descriptor.inputType(), descriptor.outputType(),
                            descriptor.hasCompensation())));
  }

  static String versionedPackage(String name, String version) {
    String nameSegment = name.toLowerCase().replaceAll("[^a-z0-9]", "");
    String versionSegment = version.replaceAll("[^a-z0-9]", "");
    return BASE_PACKAGE + "." + nameSegment + "." + versionSegment;
  }

  private String generateInterface(String pkg, String interfaceName, ProcessDescriptor descriptor) {
    String inputType = typeName(descriptor.inputType());
    String outputType = typeName(descriptor.outputType());
    List<String> imports = new ArrayList<>();
    addImport(imports, descriptor.inputType());
    addImport(imports, descriptor.outputType());

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";

    return MessageFormat.format(
            """
                    package {0};{1}
                    import io.temporal.workflow.QueryMethod;
                    import io.temporal.workflow.WorkflowInterface;
                    import io.temporal.workflow.WorkflowMethod;

                    @WorkflowInterface
                    public interface {2} '{'
                      @QueryMethod
                      String getVersion();

                      @WorkflowMethod
                      {3} run({4} input);
                    '}'
                    """,
            pkg, importBlock, interfaceName, outputType, inputType);
  }

  private String generateImpl(
          String pkg, String processName, String interfaceName, String implName, String version,
          String taskQueue, Class<?> inputType, Class<?> outputType, boolean hasCompensation) {
    String inputTypeName = typeName(inputType);
    String outputTypeName = typeName(outputType);
    List<String> imports = new ArrayList<>();
    addImport(imports, inputType);
    addImport(imports, outputType);

    String importBlock = imports.isEmpty() ? "" : "\n" + String.join("\n", imports) + "\n";

    String compensationTemplate = """
            package {0};{1}
            import cbs.nova.dsl.ExecutionMode;
            import cbs.nova.dsl.GlobalManager;
            import cbs.nova.dsl.SimpleContext;
            import io.temporal.workflow.Saga;
            import io.temporal.workflow.Workflow;

            public class {4} implements {3} '{'
              private static final String VERSION = "{5}";

              private static final String TASK_QUEUE = "{6}";

              @Override
              public String getVersion() '{'
                return VERSION;
              '}'

              @Override
              public {7} run({8} input) '{'
                Saga saga = new Saga(new Saga.Options.Builder().build());
                String runId = Workflow.getInfo().getRunId();
                var ctx = SimpleContext.getInstance().of(input, ExecutionMode.RUN, runId);
                var compensationCtx = SimpleContext.getInstance().of(input, ExecutionMode.RUN, runId);
                saga.addCompensation(
                    () ->
                        GlobalManager.getInstance().runProcess("{2}-compensation", compensationCtx));
                try '{'
                  var result = GlobalManager.getInstance().runProcess("{2}", ctx);
                  if (!result.isSuccess()) '{'
                    saga.compensate();
                    throw new RuntimeException("Process failed", result.cause());
                  '}'
                  return {9}result.value();
                '}' catch (Exception e) '{'
                  saga.compensate();
                  throw e;
                '}'
              '}'
            '}'
            """;

    String plainTemplate = """
            package {0};{1}
            import cbs.nova.dsl.ExecutionMode;
            import cbs.nova.dsl.GlobalManager;
            import cbs.nova.dsl.SimpleContext;
            import io.temporal.workflow.Workflow;

            public class {4} implements {3} '{'
              private static final String VERSION = "{5}";

              private static final String TASK_QUEUE = "{6}";

              @Override
              public String getVersion() '{'
                return VERSION;
              '}'

              @Override
              public {7} run({8} input) '{'
                String runId = Workflow.getInfo().getRunId();
                var ctx = SimpleContext.getInstance().of(input, ExecutionMode.RUN, runId);
                var result = GlobalManager.getInstance().runProcess("{2}", ctx);
                if (!result.isSuccess()) throw new RuntimeException("Process failed", result.cause());
                return {9}result.value();
              '}'
            '}'
            """;

    return MessageFormat.format(
            hasCompensation ? compensationTemplate : plainTemplate,
            pkg, importBlock, processName, interfaceName, implName, version, taskQueue,
            outputTypeName, inputTypeName, castIfNeeded(outputType));
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
    imports.add("import " + type.getName() + ";");
  }
}
