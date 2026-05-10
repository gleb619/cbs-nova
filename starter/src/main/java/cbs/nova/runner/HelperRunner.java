package cbs.nova.runner;

import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.nova.model.HelperExecutionRequest;
import cbs.nova.model.HelperExecutionResponse;
import cbs.nova.registry.DslRegistry;
import cbs.nova.temporal.workflow.GenericActivity;
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
  private final GenericActivity genericActivity;

  public HelperExecutionResponse perform(HelperExecutionRequest request) {
    log.debug(
        "Running helper: code={}, performedBy={}",
        request.helperCode(),
        request.performedBy());

    List<ParameterDefinition> definedParams =
        dslRegistry.resolveHelper(request.helperCode()).getParameters();
    Set<String> definedParamNames =
        definedParams.stream()
            .map(ParameterDefinition::getName)
            .collect(Collectors.toSet());

    Map<String, Object> filteredParams = new HashMap<>();
    for (Map.Entry<String, Object> entry : request.params().entrySet()) {
      if (definedParamNames.contains(entry.getKey())) {
        filteredParams.put(entry.getKey(), entry.getValue());
      }
    }

    HelperExecutionRequest filteredRequest =
        request.toBuilder().params(filteredParams).build();

    String executionId =
        "helper-%s-%s".formatted(request.helperCode(), UUID.randomUUID());

    HelperInput input =
        filteredRequest.toHelperInput();

    log.debug(
        "Executing helper: code={}, id={}",
        request.helperCode(),
        executionId);
    log.debug("Calling prepare on helper: code={}", request.helperCode());
    genericActivity.prepare(request.helperCode(), input.params());

    log.debug("Calling execute on helper: code={}", request.helperCode());
    HelperOutput output = genericActivity.execute(request.helperCode(), input);

    return new HelperExecutionResponse(executionId, output);
  }
}
