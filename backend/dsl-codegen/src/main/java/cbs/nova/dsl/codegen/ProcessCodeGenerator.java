package cbs.nova.dsl.codegen;

import cbs.nova.dsl.ProcessDescriptor;
import org.jspecify.annotations.NonNull;

import java.text.MessageFormat;
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
    return MessageFormat.format(
            """
            package {0};

            import io.temporal.workflow.QueryMethod;
            import io.temporal.workflow.WorkflowInterface;
            import io.temporal.workflow.WorkflowMethod;

            @WorkflowInterface
            public interface {1} '{'
              @QueryMethod
              String getVersion();

              @WorkflowMethod
              Object run(Object input);
            '}'
            """,
            pkg, interfaceName);
  }

  private String generateImpl(
          String pkg, String processName, String interfaceName, String implName, String version,
          String taskQueue, boolean hasCompensation) {
    return MessageFormat.format(
            hasCompensation
                    ? """
                    package {0};

                    import cbs.nova.dsl.ExecutionMode;
                    import cbs.nova.dsl.GlobalManager;
                    import cbs.nova.dsl.SimpleContext;
                    import io.temporal.workflow.Saga;

                    public class {3} implements {2} \u007B
                      private static final String VERSION = "{5}";

                      private static final String TASK_QUEUE = "{6}";

                      @Override
                      public String getVersion() \u007B
                        return VERSION;
                      \u007D

                      @Override
                      public Object run(Object input) \u007B
                        Saga saga = new Saga(new Saga.Options.Builder().build());
                        var ctx = SimpleContext.of(input, ExecutionMode.RUN);
                        var compensationCtx = SimpleContext.of(input, ExecutionMode.RUN);
                        saga.addCompensation(
                            () ->
                                GlobalManager.getInstance().runProcess("{1}-compensation", compensationCtx));
                        try \u007B
                          var result = GlobalManager.getInstance().runProcess("{1}", ctx);
                          if (!result.isSuccess()) \u007B
                            saga.compensate();
                            throw new RuntimeException("Process failed", result.cause());
                          \u007D
                          return result.value();
                        \u007D catch (Exception e) \u007B
                          saga.compensate();
                          throw e;
                        \u007D
                      \u007D
                    \u007D
                    """
                    : """
                    package {0};

                    import cbs.nova.dsl.ExecutionMode;
                    import cbs.nova.dsl.GlobalManager;
                    import cbs.nova.dsl.SimpleContext;

                    public class {3} implements {2} \u007B
                      private static final String VERSION = "{5}";

                      private static final String TASK_QUEUE = "{6}";

                      @Override
                      public String getVersion() \u007B
                        return VERSION;
                      \u007D

                      @Override
                      public Object run(Object input) \u007B
                        var ctx = SimpleContext.of(input, ExecutionMode.RUN);
                        var result = GlobalManager.getInstance().runProcess("{1}", ctx);
                        if (!result.isSuccess()) throw new RuntimeException("Process failed", result.cause());
                        return result.value();
                      \u007D
                    \u007D
                    """,
            pkg, processName, interfaceName, implName, version, version, taskQueue);
  }
}
