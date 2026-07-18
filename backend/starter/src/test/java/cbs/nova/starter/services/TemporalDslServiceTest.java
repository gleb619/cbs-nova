package cbs.nova.starter.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.DslTemporalProcess;
import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.function.Function;

class TemporalDslServiceTest {

  interface TestProcess extends DslTemporalProcess<String> {
  }

  static class TestProcessImpl implements TestProcess {

    volatile Function<DslTemporalProcessRequest<String>, Object> handler;

    @Override
    public Object execute(DslTemporalProcessRequest<String> request) {
      return handler.apply(request);
    }
  }

  @Test
  void executeReturnsTypedResultFromWorkflowStub() {
    String name = unique("svc-success");
    GlobalManager.globalManager().registerGeneratedClass(
            new GeneratedClassDescriptor(
                    name, DslType.PROCESS, "1.0", "tq",
                    TestProcess.class, TestProcessImpl.class, null, String.class, "{}"));

    TestProcessImpl impl = new TestProcessImpl();
    impl.handler = req -> "ok:" + req.runId() + ":" + req.payload();
    WorkflowClient client = mock(WorkflowClient.class);
    WorkerFactory factory = mock(WorkerFactory.class);
    Worker worker = mock(Worker.class);

    try (MockedStatic<WorkerFactory> staticMock = Mockito.mockStatic(WorkerFactory.class)) {
      staticMock.when(() -> WorkerFactory.newInstance(client)).thenReturn(factory);
      when(factory.newWorker(anyString())).thenReturn(worker);
      when(client.newWorkflowStub(eq(TestProcess.class), any(WorkflowOptions.class)))
              .thenReturn(impl);

      TemporalDslService service = new TemporalDslService(client);
      String result = service.execute(name, "payload", String.class);

      assertThat(result).startsWith("ok:");
      assertThat(result).contains(name);
      assertThat(result).endsWith(":payload");
      verify(factory).start();
      verify(factory).shutdown();
    }
  }

  @Test
  void executeRejectsUnknownProcessCode() {
    WorkflowClient client = mock(WorkflowClient.class);
    TemporalDslService service = new TemporalDslService(client);

    String missing = unique("missing");
    assertThatThrownBy(() -> service.execute(missing, "anything", String.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No generated DSL process:")
            .hasMessageContaining(missing);
  }

  @Test
  void executeUnwrapsCauseFromRuntimeExceptionAndRethrowsWithCode() {
    String name = unique("svc-cause");
    GlobalManager.globalManager().registerGeneratedClass(
            new GeneratedClassDescriptor(
                    name, DslType.PROCESS, "1.0", "tq",
                    TestProcess.class, TestProcessImpl.class, null, String.class, "{}"));

    IllegalStateException inner = new IllegalStateException("inner-detail");
    TestProcessImpl impl = new TestProcessImpl();
    impl.handler = req -> {
      throw new RuntimeException("outer", inner);
    };
    WorkflowClient client = mock(WorkflowClient.class);
    WorkerFactory factory = mock(WorkerFactory.class);
    Worker worker = mock(Worker.class);

    try (MockedStatic<WorkerFactory> staticMock = Mockito.mockStatic(WorkerFactory.class)) {
      staticMock.when(() -> WorkerFactory.newInstance(client)).thenReturn(factory);
      when(factory.newWorker(anyString())).thenReturn(worker);
      when(client.newWorkflowStub(eq(TestProcess.class), any(WorkflowOptions.class)))
              .thenReturn(impl);

      TemporalDslService service = new TemporalDslService(client);
      assertThatThrownBy(() -> service.execute(name, "any", String.class))
              .isInstanceOf(RuntimeException.class)
              .hasMessageContaining("DSL workflow " + name + " failed: inner-detail")
              .hasCauseInstanceOf(IllegalStateException.class);

      verify(factory).shutdown();
    }
  }

  @Test
  void executePropagatesOriginalExceptionWhenNoCauseIsPresent() {
    String name = unique("svc-no-cause");
    GlobalManager.globalManager().registerGeneratedClass(
            new GeneratedClassDescriptor(
                    name, DslType.PROCESS, "1.0", "tq",
                    TestProcess.class, TestProcessImpl.class, null, String.class, "{}"));

    TestProcessImpl impl = new TestProcessImpl();
    impl.handler = req -> {
      throw new IllegalArgumentException("original");
    };
    WorkflowClient client = mock(WorkflowClient.class);
    WorkerFactory factory = mock(WorkerFactory.class);
    Worker worker = mock(Worker.class);

    try (MockedStatic<WorkerFactory> staticMock = Mockito.mockStatic(WorkerFactory.class)) {
      staticMock.when(() -> WorkerFactory.newInstance(client)).thenReturn(factory);
      when(factory.newWorker(anyString())).thenReturn(worker);
      when(client.newWorkflowStub(eq(TestProcess.class), any(WorkflowOptions.class)))
              .thenReturn(impl);

      TemporalDslService service = new TemporalDslService(client);
      assertThatThrownBy(() -> service.execute(name, "any", String.class))
              .isInstanceOf(RuntimeException.class)
              .hasMessageContaining("DSL workflow " + name + " failed: original")
              .hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }
}
