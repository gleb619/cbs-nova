package cbs.nova.runner;

import cbs.dsl.api.EventOperation;
import cbs.dsl.api.EventTypes.EventStatus;
import cbs.dsl.api.ParameterDefinition;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.registry.DefaultSpecDefinitionRegistry;
import cbs.nova.registry.DslRegistry;
import cbs.nova.temporal.WorkflowManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventRunner {

  private final WorkflowManager workflowManager;
  private final DslRegistry dslRegistry;
  private final DefaultSpecDefinitionRegistry specRegistry;

  /**
   * Runs an event by starting its generated Temporal workflow.
   *
   * @param request the execution request containing eventCode, params, performer
   * @return the execution response with executionId and status
   */
  public EventExecutionResponse perform(EventExecutionRequest request) {
    log.debug("Running event: code={}, performedBy={}", request.eventCode(), request.performedBy());

    specRegistry.getWorkflowInterface(request.eventCode());

    List<ParameterDefinition> definedParams =
        dslRegistry.resolveEvent(request.eventCode()).getParameters();
    Set<String> definedParamNames =
        definedParams.stream().map(ParameterDefinition::getName).collect(Collectors.toSet());

    Map<String, Object> filteredParams = new HashMap<>();
    for (Map.Entry<String, Object> entry : request.params().entrySet()) {
      if (definedParamNames.contains(entry.getKey())) {
        filteredParams.put(entry.getKey(), entry.getValue());
      }
    }

    EventExecutionRequest filteredRequest =
        request.toBuilder().params(filteredParams).build();

    String workflowId = generateWorkflowId(request);

    EventOperation workflowStub = workflowManager.newWorkflowStub(request.eventCode(), workflowId);

    log.debug("Starting Temporal workflow: code={}, id={}", request.eventCode(), workflowId);
    // TODO: generate and add a event number here
    String eventNumber = "123abc";
    workflowManager.start(workflowStub, filteredRequest.toEventInput(eventNumber));

    return new EventExecutionResponse(eventNumber, EventStatus.PENDING);
  }

  private String generateWorkflowId(EventExecutionRequest request) {
    return "event-%s-%s".formatted(request.eventCode(), UUID.randomUUID());
  }
}
