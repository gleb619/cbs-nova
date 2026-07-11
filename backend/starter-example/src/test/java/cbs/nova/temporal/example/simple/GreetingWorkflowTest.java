package cbs.nova.temporal.example.simple;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GreetingWorkflowTest {

  private static final String TASK_QUEUE = "greeting-task-queue";

  private TestWorkflowEnvironment testEnv;

  @BeforeEach
  void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnv.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    testEnv.start();
  }

  @AfterEach
  void tearDown() {
    testEnv.shutdown();
  }

  @Test
  void greetsByName() {
    GreetingWorkflow workflow = testEnv
            .getWorkflowClient()
            .newWorkflowStub(
                    GreetingWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());

    assertThat(workflow.greet("Temporal")).isEqualTo("Hello, Temporal!");
  }
}
