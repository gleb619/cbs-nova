package cbs.dsl.builder;

import cbs.dsl.api.Action;
import cbs.dsl.api.DslDefinitionCollector;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.TransitionRuleDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builder for creating {@link WorkflowDefinition} instances.
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
public class WorkflowBuilder {

  private final String code;
  private final List<String> states = new ArrayList<>();
  private String initial;
  private final List<String> terminalStates = new ArrayList<>();
  private final List<TransitionRuleDefinition> transitions = new ArrayList<>();

  WorkflowBuilder(String code) {
    this.code = code;
  }

  public WorkflowBuilder states(String... states) {
    Collections.addAll(this.states, states);
    return this;
  }

  public WorkflowBuilder initial(String initial) {
    this.initial = initial;
    return this;
  }

  public WorkflowBuilder terminal(String... states) {
    Collections.addAll(this.terminalStates, states);
    return this;
  }

  public WorkflowBuilder transition(
      String from, String to, Action action, EventDefinition event) {
    this.transitions.add(new TransitionRuleDefinition(from, to, action, event));
    return this;
  }

  public WorkflowDefinition build() {
    List<String> st = Collections.unmodifiableList(new ArrayList<>(states));
    List<String> terminal = Collections.unmodifiableList(new ArrayList<>(terminalStates));
    List<TransitionRuleDefinition> tx = Collections.unmodifiableList(new ArrayList<>(transitions));
    String ini = initial != null && !initial.isEmpty() ? initial : st.isEmpty() ? null : st.getFirst();

    WorkflowDefinition def = new WorkflowDefinition() {
      @Override
      public String getCode() {
        return code;
      }

      @Override
      public List<String> getStates() {
        return st;
      }

      @Override
      public String getInitial() {
        return ini;
      }

      @Override
      public List<String> getTerminalStates() {
        return terminal;
      }

      @Override
      public List<TransitionRuleDefinition> getTransitions() {
        return tx;
      }

      @Override
      public WorkflowOutput execute(WorkflowInput input) {
        return new WorkflowOutput("DONE", List.of(), "ACTIVE");
      }
    };
    DslDefinitionCollector.register(def);
    return def;
  }
}
