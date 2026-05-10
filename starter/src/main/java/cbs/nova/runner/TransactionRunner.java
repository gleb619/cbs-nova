package cbs.nova.runner;

import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionStatus;
import cbs.nova.model.TransactionExecutionRequest;
import cbs.nova.model.TransactionExecutionResponse;
import cbs.nova.registry.DefaultSpecDefinitionRegistry;
import cbs.nova.registry.DslRegistry;
import cbs.nova.temporal.WorkflowManager;
import cbs.nova.temporal.workflow.GenericTransactionRequest;
import cbs.nova.temporal.workflow.GenericWorkflow;
import io.temporal.client.WorkflowClient;
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
public class TransactionRunner {

  private final WorkflowManager workflowManager;
  private final DslRegistry dslRegistry;
  private final DefaultSpecDefinitionRegistry specRegistry;

  /**
   * Runs a transaction by starting its generic Temporal workflow.
   *
   * @param request the execution request containing transactionCode, params, performedBy
   * @return the execution response with executionId and status
   */
  public TransactionExecutionResponse perform(TransactionExecutionRequest request) {
    log.debug(
        "Running transaction: code={}, performedBy={}",
        request.transactionCode(),
        request.performedBy());

    specRegistry.getActivityInterface(request.transactionCode());

    List<ParameterDefinition> definedParams =
        dslRegistry.resolveTransaction(request.transactionCode()).getParameters();
    Set<String> definedParamNames =
        definedParams.stream().map(ParameterDefinition::getName).collect(Collectors.toSet());

    Map<String, Object> filteredParams = new HashMap<>();
    for (Map.Entry<String, Object> entry : request.params().entrySet()) {
      if (definedParamNames.contains(entry.getKey())) {
        filteredParams.put(entry.getKey(), entry.getValue());
      }
    }

    TransactionExecutionRequest filteredRequest =
        request.toBuilder().params(filteredParams).build();

    String workflowId = "transaction-%s-%s".formatted(request.transactionCode(), UUID.randomUUID());

    TransactionInput input =
        filteredRequest.toTransactionInput(UUID.randomUUID().toString());

    GenericWorkflow stub = workflowManager.newWorkflowStub(GenericWorkflow.class, workflowId);

    GenericTransactionRequest genericRequest =
        new GenericTransactionRequest(request.transactionCode(), input);

    log.debug(
        "Starting generic transaction workflow: code={}, id={}",
        request.transactionCode(),
        workflowId);
    WorkflowClient.start(stub::execute, genericRequest);

    return new TransactionExecutionResponse(workflowId, TransactionStatus.PENDING);
  }
}
