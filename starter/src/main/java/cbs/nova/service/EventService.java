package cbs.nova.service;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.nova.model.EventExecutionRequest;
import cbs.nova.model.EventExecutionResponse;
import cbs.nova.model.WorkflowExecutionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
// TODO: incorrectly implemented
@Deprecated(forRemoval = true)
public class EventService {

  private final WorkflowResolver workflowResolver;
  private final ContextEvaluator contextEvaluator;
  private final ContextEncryptionService contextEncryptionService;
  private final WorkflowExecutor workflowExecutor;

  // TODO: incorrectly implemented
  @Deprecated(forRemoval = true)
  public EventExecutionResponse execute(EventExecutionRequest request) {
    log.debug(
        "Executing event: workflow={}, event={}", request.workflowCode(), request.eventCode());

    // 1. Resolve workflow definition
    WorkflowDefinition workflowDefinition = workflowResolver.resolve(request.workflowCode());

    // 2. Resolve event definition
    EventDefinition eventDefinition = workflowResolver.resolveEvent(request.eventCode());

    // 3. Evaluate context via DSL contextBlock
    Map<String, Object> enrichedContext = contextEvaluator.evaluate(eventDefinition, request);

    // 4. Encrypt sensitive fields
    String encryptedContextJson = contextEncryptionService.encrypt(enrichedContext);

    // 5. Launch Temporal workflow
    WorkflowExecutionResponse result = workflowExecutor.start(request, encryptedContextJson);

    // 6. Build response
    return new EventExecutionResponse(result.executionId(), result.status());
  }
}
