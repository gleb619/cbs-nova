package cbs.nova.service;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.model.WorkflowExecutionResponse;
import cbs.nova.registry.DslRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventExecutionService {

  private final WorkflowResolver workflowResolver;
  private final WorkflowExecutor workflowExecutor;
  private final ContextEncryptionService contextEncryptionService;
  private final DslRegistry dslRegistry;

  public EventExecutionResponse execute(EventExecutionRequest request) {
    log.debug(
        "Executing event: workflow={}, event={}", request.workflowCode(), request.eventCode());

    WorkflowDefinition workflowDefinition = workflowResolver.resolve(request.workflowCode());
    log.debug("Resolved workflow: {}", workflowDefinition.getCode());

    EventDefinition eventDef = workflowResolver.resolveEvent(request.eventCode());

    EnrichmentContext enrichmentContext = new EnrichmentContext(
        request.eventCode(), 0L, request.performedBy(), "dev", request.parameters());
    enrichmentContext.setHelperResolver((name, params) -> dslRegistry
        .resolveHelper(name)
        .execute(new HelperInput(params, request.eventCode(), null))
        .value());
    eventDef.getContextBlock().accept(enrichmentContext);

    Map<String, Object> enrichedContext = new HashMap<>(request.parameters());
    enrichedContext.putAll(enrichmentContext.getEnrichment());

    String encryptedContextJson = contextEncryptionService.encrypt(enrichedContext);

    WorkflowExecutionResponse result =
        workflowExecutor.start(request, encryptedContextJson, eventDef.getTransactionCodes());
    return new EventExecutionResponse(result.executionId(), result.status());
  }
}
