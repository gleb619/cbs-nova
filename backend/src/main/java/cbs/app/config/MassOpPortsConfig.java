package cbs.app.config;

import cbs.app.temporal.massop.MassOpInput;
import cbs.app.temporal.massop.MassOpSignal;
import cbs.app.temporal.massop.MassOpSignalType;
import cbs.app.temporal.massop.MassOpWorkflow;
import cbs.nova.service.MassOpSignalPort;
import cbs.nova.service.MassOpTriggerPort;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MassOpPortsConfig {

  private final WorkflowClient workflowClient;

  @Bean
  public MassOpTriggerPort massOpTriggerPort(@Value("${app.temporal.task-queue}") String taskQueue) {
    return (massOpCode, performedBy, dslVersion, contextJson, workflowId) -> {
      WorkflowOptions options = WorkflowOptions.newBuilder()
          .setWorkflowId(workflowId)
          .setTaskQueue(taskQueue)
          .build();
      MassOpWorkflow stub = workflowClient.newWorkflowStub(MassOpWorkflow.class, options);
      WorkflowClient.start(
          stub::execute, new MassOpInput(massOpCode, performedBy, dslVersion, contextJson));
      return workflowId;
    };
  }

  @Bean
  public MassOpSignalPort massOpSignalPort() {
    return (temporalWorkflowId, signalType) -> {
      MassOpWorkflow stub =
          workflowClient.newWorkflowStub(MassOpWorkflow.class, temporalWorkflowId);
      MassOpSignalType type = MassOpSignalType.valueOf(signalType);
      stub.signal(new MassOpSignal(type));
    };
  }
}
