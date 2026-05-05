package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.TransitionRuleDefinition;
import cbs.dsl.api.WorkflowDefinition;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Concrete DSL object for workflows — implements {@link WorkflowDefinition} and {@link DslObject}. */
@Getter
@RequiredArgsConstructor
public class WorkflowDslObject implements DslObject {

  private final String code;
  private final List<String> states;
  private final String initial;
  private final List<String> terminalStates;
  private final List<TransitionRuleDefinition> transitions;

  public DslObject dsl() {
    return this;
  }
}
