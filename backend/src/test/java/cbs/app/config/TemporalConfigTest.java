package cbs.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TemporalConfigTest {

  @Test
  @DisplayName("Should create WorkflowServiceStubs bean with correct target")
  void shouldCreateWorkflowServiceStubsBeanWithCorrectTarget() {
    var config = new TemporalConfig();
    ReflectionTestUtils.setField(config, "serviceAddress", "127.0.0.1:7233");

    WorkflowServiceStubs stubs = config.workflowServiceStubs();

    assertThat(stubs).isNotNull();
    stubs.shutdown();
  }

  @Test
  @DisplayName("Should create WorkflowClient bean from stubs")
  void shouldCreateWorkflowClientBeanFromStubs() {
    var config = new TemporalConfig();

    WorkflowServiceStubs stubs = WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget("127.0.0.1:7233").build());

    WorkflowClient client = config.workflowClient(stubs);

    assertThat(client).isNotNull();
    stubs.shutdown();
  }

  @Test
  @DisplayName("Should create WorkerFactory bean from client")
  void shouldCreateWorkerFactoryBeanFromClient() {
    var config = new TemporalConfig();

    WorkflowServiceStubs stubs = WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget("127.0.0.1:7233").build());
    WorkflowClient client = WorkflowClient.newInstance(stubs);

    WorkerFactory factory = config.workerFactory(client);

    assertThat(factory).isNotNull();
    stubs.shutdown();
  }
}
