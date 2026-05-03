package cbs.dsl.builder;

/**
 * Entry point for the workflow definition DSL.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * WorkflowDefinition wf = WorkflowDsl.workflow("LOAN_CONTRACT")
 *     .states("DRAFT", "ENTERED", "ACTIVE", "CLOSED")
 *     .initial("ENTERED")
 *     .terminal("CLOSED")
 *     .transition("ENTERED", "ACTIVE", Action.APPROVE, eventDef)
 *     .build();
 * }</pre>
 */
public final class WorkflowDsl {

  private WorkflowDsl() {}

  public static WorkflowBuilder workflow(String code) {
    return new WorkflowBuilder(code);
  }
}
