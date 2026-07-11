package cbs.nova.temporal.example.medium;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParallelAggregationWorkflowTest {
  private static final String TASK_QUEUE = "aggregation-task-queue";

  private TestWorkflowEnvironment testEnv;

  @BeforeEach
  void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnv.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(ParallelAggregationWorkflowImpl.class);
    worker.registerActivitiesImplementations(new AggregationActivitiesImpl());
    testEnv.start();
  }

  @AfterEach
  void tearDown() {
    testEnv.shutdown();
  }

  @Test
  void aggregatesParallelResults() {
    ParallelAggregationWorkflow workflow = testEnv
            .getWorkflowClient()
            .newWorkflowStub(
                    ParallelAggregationWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());

    assertThat(workflow.aggregate("data")).isEqualTo("A-data B-data C-data");
  }
}
