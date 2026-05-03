package cbs.nova.service;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.nova.model.EventExecutionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
// TODO: incorrectly implemented
@Deprecated(forRemoval = true)
public class ContextEvaluator {

  @SuppressWarnings("unchecked")
  // TODO: incorrectly implemented
  @Deprecated(forRemoval = true)
  public Map<String, Object> evaluate(EventDefinition eventDef, EventExecutionRequest request) {
    EnrichmentContext enrichmentContext = new EnrichmentContext(
        request.eventCode(),
        0L,
        request.performedBy(),
        "dev", // TODO(T06): replace with DslRegistry.getDslVersion()
        request.parameters());

    eventDef.getContextBlock().invoke(enrichmentContext);

    Map<String, Object> result = new HashMap<>(request.parameters());
    result.putAll(enrichmentContext.getEnrichment());
    return result;
  }
}
