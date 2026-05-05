package cbs.dsl.evaluator;

import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.MassOperationContext;
import cbs.dsl.api.context.TransactionContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates context objects for all business entities.
 *
 * <p>Provides factory methods to construct the appropriate context type from raw inputs or
 * parent contexts.
 */
public class ContextCreator {

  private ContextCreator() {}

  /**
   * Creates a {@link TransactionContext} from the given root context map.
   *
   * @param eventCode the event code
   * @param eventNumber the event number
   * @param workflowExecutionId the workflow execution id
   * @param environment the environment
   * @param params the parameter map
   * @param preview whether this is a preview
   * @return the transaction context
   */
  public static TransactionContext createTransactionContext(
      String eventCode,
      Long eventNumber,
      String workflowExecutionId,
      String environment,
      Map<String, Object> params,
      boolean preview) {
    return TransactionContext.builder()
        .eventCode(eventCode)
        .workflowExecutionId(eventNumber)
        .performedBy(workflowExecutionId)
        .dslVersion(environment)
        .eventParameters(params)
        .enrichment(new HashMap<>())
        .helperResolver(null)
        .isResumed(preview)
        .build();
  }

  /**
   * Creates a {@link TransactionContext} from a {@link TransactionInput}.
   *
   * @param input the transaction input
   * @param preview whether this is a preview
   * @return the transaction context
   */
  public static TransactionContext createTransactionContext(TransactionInput input, boolean preview) {
    return TransactionContext.builder()
        .eventCode(input.eventCode())
        .workflowExecutionId(input.eventNumber())
        .performedBy(input.workflowExecutionId())
        .dslVersion("dev")
        .eventParameters(input.params() != null ? input.params() : Collections.emptyMap())
        .enrichment(new HashMap<>())
        .helperResolver(null)
        .isResumed(preview)
        .build();
  }

  /**
   * Creates an {@link EnrichmentContext} from basic parameters.
   *
   * @param eventCode the event code
   * @param eventNumber the event number
   * @param user the user
   * @param environment the environment
   * @param params the parameter map
   * @return the enrichment context
   */
  public static EnrichmentContext createEnrichmentContext(
      String eventCode,
      Long eventNumber,
      String user,
      String environment,
      Map<String, Object> params) {
    return EnrichmentContext.builder()
        .eventCode(eventCode)
        .workflowExecutionId(eventNumber)
        .performedBy(user)
        .dslVersion(environment)
        .eventParameters(params)
        .enrichment(new HashMap<>())
        .helperResolver(null)
        .build();
  }

  /**
   * Creates an {@link EnrichmentContext} from an {@link EventInput}.
   *
   * @param input the event input
   * @param user the user
   * @return the enrichment context
   */
  public static EnrichmentContext createEnrichmentContext(EventInput input, String user) {
    return EnrichmentContext.builder()
        .eventCode(input.eventCode())
        .workflowExecutionId(input.eventNumber())
        .performedBy(user)
        .dslVersion("dev")
        .eventParameters(input.params() != null ? input.params() : Collections.emptyMap())
        .enrichment(new HashMap<>())
        .helperResolver(null)
        .build();
  }

  /**
   * Creates a {@link MassOperationContext}.
   *
   * @param params the parameter map
   * @return the mass operation context
   */
  public static MassOperationContext createMassOperationContext(Map<String, Object> params) {
    return MassOperationContext.builder()
        .performedBy("")
        .dslVersion("dev")
        .enrichment(params != null ? params : new HashMap<>())
        .build();
  }

  /**
   * Creates a {@link WorkflowOutput} from a {@link WorkflowInput}.
   *
   * @param input the workflow input
   * @return the workflow output
   */
  public static WorkflowOutput createWorkflowOutput(WorkflowInput input) {
    return new WorkflowOutput("DONE");
  }
}