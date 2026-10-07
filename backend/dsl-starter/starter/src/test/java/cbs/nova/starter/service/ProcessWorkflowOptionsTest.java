package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import io.temporal.client.WorkflowOptions;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class ProcessWorkflowOptionsTest {

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().registerGeneratedClass(
            new GeneratedClassDescriptor(
                    "TestProc", DslType.PROCESS, "1.0", "test-queue",
                    TestWorkflow.class, TestWorkflowImpl.class, null, null, "{}"));
  }

  @Test
  void forProcessSetsTaskQueueAndTimeoutsAndRetryPolicy() {
    GeneratedClassDescriptor descriptor = GlobalManager.globalManager()
            .findGeneratedProcess("TestProc")
            .orElseThrow();

    WorkflowOptions options = ProcessWorkflowOptions.forProcess(descriptor,
            new ProcessWorkflowOptions.Properties(Duration.ofMinutes(7), Duration.ofSeconds(3)))
            .build();

    assertThat(options.getTaskQueue()).isEqualTo("test-queue");
    assertThat(options.getWorkflowExecutionTimeout()).isEqualTo(Duration.ofMinutes(7));
    assertThat(options.getWorkflowTaskTimeout()).isEqualTo(Duration.ofSeconds(3));
    assertThat(options.getRetryOptions().getMaximumAttempts()).isEqualTo(1);
  }

  @Test
  void forProcessReturnsBuilderThatCallerCanExtend() {
    GeneratedClassDescriptor descriptor = GlobalManager.globalManager()
            .findGeneratedProcess("TestProc")
            .orElseThrow();

    WorkflowOptions options = ProcessWorkflowOptions.forProcess(descriptor,
            new ProcessWorkflowOptions.Properties(Duration.ofMinutes(7), Duration.ofSeconds(3)))
            .setWorkflowId("wf-1")
            .build();

    assertThat(options.getWorkflowId()).isEqualTo("wf-1");
  }

  @WorkflowInterface
  public interface TestWorkflow {
    @WorkflowMethod
    String execute(Object request);
  }

  public static class TestWorkflowImpl implements TestWorkflow {
    @Override
    public String execute(Object request) {
      return "ok";
    }
  }
}
