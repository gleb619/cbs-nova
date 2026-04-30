package cbs.nova.service;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.nova.model.exception.EntityNotFoundException;
import cbs.nova.registry.DslRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowResolver {

  private final DslRegistry dslRegistry;

  public WorkflowDefinition resolve(String workflowCode) {
    WorkflowDefinition definition = dslRegistry.getWorkflows().get(workflowCode);
    if (definition == null) {
      throw new EntityNotFoundException("WorkflowDefinition", workflowCode);
    }
    return definition;
  }

  public EventDefinition resolveEvent(String eventCode) {
    EventDefinition definition = dslRegistry.getEvents().get(eventCode);
    if (definition == null) {
      throw new EntityNotFoundException("EventDefinition", eventCode);
    }
    return definition;
  }
}
