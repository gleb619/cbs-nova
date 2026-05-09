package cbs.dsl.builder;

import cbs.dsl.api.Action;
import cbs.dsl.api.DslObject;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.TransitionRuleDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builder for creating workflow objects from DSL files. */
public class WorkflowBuilder {

  private final String code;
  private final List<String> states = new ArrayList<>();
  private String initialState;
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
    this.initialState = initial;
    return this;
  }

  public WorkflowBuilder terminal(String... states) {
    Collections.addAll(this.terminalStates, states);
    return this;
  }

  public WorkflowBuilder transition(String from, String to, Action action, EventDefinition event) {
    this.transitions.add(new TransitionRuleDefinition(from, to, action, event));
    return this;
  }

  @Deprecated(forRemoval = true)
  public WorkflowBuilder transition(String from, String to, Action action, String eventCode) {
    EventDefinition proxy = new EventDefinition() {
      @Override
      public String getCode() {
        return eventCode;
      }

      @Override
      public List<ParameterDefinition> getParameters() {
        return Collections.emptyList();
      }
    };
    this.transitions.add(new TransitionRuleDefinition(from, to, action, proxy));
    return this;
  }

  public String getCode() {
    return code;
  }

  public List<String> states() {
    return Collections.unmodifiableList(new ArrayList<>(states));
  }

  public String initial() {
    return initialState;
  }

  public List<String> terminalStates() {
    return Collections.unmodifiableList(new ArrayList<>(terminalStates));
  }

  public List<TransitionRuleDefinition> transitions() {
    return Collections.unmodifiableList(new ArrayList<>(transitions));
  }

  public DslObject build() {
    return WorkflowDslObject.builder()
        .code(code)
        .states(Collections.unmodifiableList(new ArrayList<>(states)))
        .initial(initialState)
        .terminalStates(Collections.unmodifiableList(new ArrayList<>(terminalStates)))
        .transitions(Collections.unmodifiableList(new ArrayList<>(transitions)))
        .build();
  }
}
