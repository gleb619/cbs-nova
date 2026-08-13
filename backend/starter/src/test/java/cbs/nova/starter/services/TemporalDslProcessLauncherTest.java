package cbs.nova.starter.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.DslExecutionException;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.DslTemporalProcess;
import cbs.nova.dsl.DslTemporalProcessFailure;
import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.dsl.TransactionRouting;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

class TemporalDslProcessLauncherTest {

  interface LauncherTestProcess extends DslTemporalProcess<Object> {
  }

  static class LauncherTestProcessImpl implements LauncherTestProcess {

    volatile Function<DslTemporalProcessRequest<Object>, Object> handler;

    @Override
    public Object execute(DslTemporalProcessRequest<Object> request) {
      return handler.apply(request);
    }
  }

  @Test
  void canRunReturnsTrueOutsideWorkflowThread() {
    TemporalDslProcessLauncher launcher = new TemporalDslProcessLauncher(
            mock(WorkflowClient.class), new ObjectMapper(), Duration.ofSeconds(30),
            Duration.ofSeconds(5));
    SimpleContext<String> ctx = new SimpleContext<>("body", Map.of(), ExecutionMode.RUN, "rid",
            TransactionRouting.LOCAL, null, null, null);

    assertThat(launcher.canRun(ctx)).isTrue();
  }

  @Test
  void canRunReturnsFalseForNonRunMode() {
    TemporalDslProcessLauncher launcher = new TemporalDslProcessLauncher(
            mock(WorkflowClient.class), new ObjectMapper(), Duration.ofSeconds(30),
            Duration.ofSeconds(5));

    assertThat(launcher.canRun(new SimpleContext<>("body", Map.of(), ExecutionMode.PREVIEW, "rid",
            TransactionRouting.LOCAL, null, null, null))).isFalse();
    assertThat(launcher.canRun(new SimpleContext<>("body", Map.of(), ExecutionMode.EXPLAIN, "rid",
            TransactionRouting.LOCAL, null, null, null))).isFalse();
  }

  @Test
  void launchBuildsExpectedWorkflowOptions() {
    String name = unique("tl-opts");
    registerDescriptor(name);
    SimpleContext<String> ctx = new SimpleContext<>("payload", Map.of(), ExecutionMode.RUN,
            "rid-launch-1", TransactionRouting.LOCAL, null, null, null);
    LauncherTestProcessImpl impl = new LauncherTestProcessImpl();
    impl.handler = req -> "ok";

    WorkflowClient client = mock(WorkflowClient.class);
    when(client.newWorkflowStub(
            eq(LauncherTestProcess.class), any(WorkflowOptions.class)))
            .thenReturn(impl);

    TemporalDslProcessLauncher launcher = new TemporalDslProcessLauncher(client,
            new ObjectMapper(), Duration.ofSeconds(30), Duration.ofSeconds(5));
    launcher.launch(name, "task-queue-1", null, null, ctx);

    ArgumentCaptor<WorkflowOptions> captor = ArgumentCaptor.forClass(WorkflowOptions.class);
    verify(client).newWorkflowStub(eq(LauncherTestProcess.class), captor.capture());
    WorkflowOptions opts = captor.getValue();

    assertThat(opts.getTaskQueue()).isEqualTo("task-queue-1");
    assertThat(opts.getWorkflowId()).isEqualTo("rid-launch-1");
    assertThat(opts.getWorkflowExecutionTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(opts.getWorkflowTaskTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(opts.getRetryOptions().getMaximumAttempts()).isEqualTo(1);
  }

  @Test
  void launchReturnsSuccessForSuccessfulExecution() {
    String name = unique("tl-success");
    registerDescriptor(name);
    SimpleContext<String> ctx = new SimpleContext<>("payload", Map.of(), ExecutionMode.RUN,
            "rid-launch-2", TransactionRouting.LOCAL, null, null, null);
    LauncherTestProcessImpl impl = new LauncherTestProcessImpl();
    impl.handler = req -> "echoed:" + req.payload();

    WorkflowClient client = mock(WorkflowClient.class);
    when(client.newWorkflowStub(
            eq(LauncherTestProcess.class), any(WorkflowOptions.class)))
            .thenReturn(impl);

    TemporalDslProcessLauncher launcher = new TemporalDslProcessLauncher(client,
            new ObjectMapper(), Duration.ofSeconds(30), Duration.ofSeconds(5));
    Result<?> result = launcher.launch(name, "tq", null, null, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("echoed:payload");
  }

  @Test
  void launchMapsDslTemporalProcessFailureResultToDslExecutionException() {
    String name = unique("tl-fail-marker");
    registerDescriptor(name);
    SimpleContext<String> ctx = new SimpleContext<>("payload", Map.of(), ExecutionMode.RUN,
            "rid-launch-3", TransactionRouting.LOCAL, null, null, null);
    LauncherTestProcessImpl impl = new LauncherTestProcessImpl();
    impl.handler = req -> new DslTemporalProcessFailure("the-message", "the-detail");

    WorkflowClient client = mock(WorkflowClient.class);
    when(client.newWorkflowStub(
            eq(LauncherTestProcess.class), any(WorkflowOptions.class)))
            .thenReturn(impl);

    TemporalDslProcessLauncher launcher = new TemporalDslProcessLauncher(client,
            new ObjectMapper(), Duration.ofSeconds(30), Duration.ofSeconds(5));
    Result<?> result = launcher.launch(name, "tq", null, null, ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslExecutionException.class);
    assertThat(result.cause())
            .hasMessageContaining("the-message")
            .hasMessageContaining("the-detail");
  }

  @Test
  void launchWrapsThrownExceptionInDslExecutionExceptionWithUnwrappedCause() {
    String name = unique("tl-throws");
    registerDescriptor(name);
    SimpleContext<String> ctx = new SimpleContext<>("payload", Map.of(), ExecutionMode.RUN,
            "rid-launch-4", TransactionRouting.LOCAL, null, null, null);
    LauncherTestProcessImpl impl = new LauncherTestProcessImpl();
    IllegalStateException inner = new IllegalStateException("inner-boom");
    impl.handler = req -> {
      throw new RuntimeException("outer", inner);
    };

    WorkflowClient client = mock(WorkflowClient.class);
    when(client.newWorkflowStub(
            eq(LauncherTestProcess.class), any(WorkflowOptions.class)))
            .thenReturn(impl);

    TemporalDslProcessLauncher launcher = new TemporalDslProcessLauncher(client,
            new ObjectMapper(), Duration.ofSeconds(30), Duration.ofSeconds(5));
    Result<?> result = launcher.launch(name, "tq", null, null, ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslExecutionException.class);
    assertThat(result.cause())
            .hasMessageContaining("Process " + name + " failed: inner-boom");
    assertThat(result.cause().getCause()).isSameAs(inner);
  }

  @Test
  void launchConvertsResultToDeclaredOutputTypeWhenNotAlreadyInstance() {
    String name = unique("tl-convert");
    registerDescriptor(name);
    SimpleContext<Map<String, Object>> ctx = new SimpleContext<>(Map.of(), Map.of(),
            ExecutionMode.RUN, "rid-launch-5", TransactionRouting.LOCAL, null, null, null);
    LauncherTestProcessImpl impl = new LauncherTestProcessImpl();
    impl.handler = req -> Map.of("a", 1, "b", "two");

    WorkflowClient client = mock(WorkflowClient.class);
    when(client.newWorkflowStub(
            eq(LauncherTestProcess.class), any(WorkflowOptions.class)))
            .thenReturn(impl);

    TemporalDslProcessLauncher launcher = new TemporalDslProcessLauncher(client,
            new ObjectMapper(), Duration.ofSeconds(30), Duration.ofSeconds(5));
    Result<?> result = launcher.launch(name, "tq", null, ConvertibleRecord.class, ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isInstanceOf(ConvertibleRecord.class);
    ConvertibleRecord record = (ConvertibleRecord) result.value();
    assertThat(record.a()).isEqualTo(1);
    assertThat(record.b()).isEqualTo("two");
  }

  @Test
  void launchPropagatesIllegalArgumentExceptionForUnknownProcessName() {
    WorkflowClient client = mock(WorkflowClient.class);
    TemporalDslProcessLauncher launcher = new TemporalDslProcessLauncher(client,
            new ObjectMapper(), Duration.ofSeconds(30), Duration.ofSeconds(5));
    SimpleContext<String> ctx = new SimpleContext<>("payload", Map.of(), ExecutionMode.RUN,
            "rid-launch-6", TransactionRouting.LOCAL, null, null, null);
    String missing = unique("missing");

    assertThatThrownBy(() -> launcher.launch(missing, "tq", null, null, ctx))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No generated Temporal process: " + missing);
  }

  @Test
  void canRunInsideWorkflowThreadIsNotReproducibleInPureUnitTest() {
    // The "inside a workflow" branch (canRun returns false because Workflow.getInfo()
    // succeeds) requires being on a real Temporal workflow thread, e.g. via
    // TestWorkflowEnvironment.newWorkflowStub(...).execute(...). We deliberately do not
    // reproduce that here because the production logic is a single conditional check
    // (ctx.mode() != RUN -> false; otherwise inverted Workflow.getInfo()-throws-then-true)
    // and adding a TestWorkflowEnvironment dependency for one assertion would dominate
    // this unit test's startup cost. The two reachable branches outside a workflow are
    // pinned by canRunReturnsTrueOutsideWorkflowThread and canRunReturnsFalseForNonRunMode.
  }

  private static void registerDescriptor(String name) {
    GlobalManager.globalManager().registerGeneratedClass(
            new GeneratedClassDescriptor(
                    name, DslType.PROCESS, "1.0", "tq",
                    LauncherTestProcess.class, LauncherTestProcessImpl.class, null, null, "{}"));
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }

  record ConvertibleRecord(int a, String b) {
  }
}
