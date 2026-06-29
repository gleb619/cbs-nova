package cbs.nova.runner;

import cbs.dsl.api.ContextTypes.ContextOutput;
import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.ParameterDefinition;
import cbs.nova.model.HelperExecutionRequest;
import cbs.nova.model.HelperExecutionResponse;
import cbs.nova.registry.DslRegistry;
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
public class HelperRunner {

  private final DslRegistry dslRegistry;

  public HelperExecutionResponse perform(HelperExecutionRequest request) {
    log.debug("Running helper: code={}, performedBy={}", request.helperCode(), request.performedBy());

    HelperDefinition helperDefinition = dslRegistry.resolveHelper(request.helperCode());
    List<ParameterDefinition> definedParams =
        helperDefinition.getParameters();
    Set<String> definedParamNames =
        definedParams.stream().map(ParameterDefinition::getName).collect(Collectors.toSet());

    String executionId = "helper-%s-%s".formatted(request.helperCode(), UUID.randomUUID());
    log.debug("Executing helper: code={}, id={}", request.helperCode(), executionId);

    Map<String, Object> filteredParams = new HashMap<>();
    for (Map.Entry<String, Object> entry : request.params().entrySet()) {
      if (definedParamNames.contains(entry.getKey())) {
        filteredParams.put(entry.getKey(), entry.getValue());
      }
    }

    log.debug("Calling prepare on helper: code={}", request.helperCode());
    ContextOutput contextOutput = helperDefinition.prepare(filteredParams);

    HelperInput input = HelperInput.builder()
        .eventNumber(request.eventNumber())
        .params(contextOutput.params())
        .build();

    log.debug("Calling execute on helper: code={}", request.helperCode());
    HelperOutput output = helperDefinition.execute(input);

    return new HelperExecutionResponse(executionId, output);
  }
}
