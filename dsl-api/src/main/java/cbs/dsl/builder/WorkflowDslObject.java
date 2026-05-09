package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.TransitionRuleDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record WorkflowDslObject(
    String code,
    List<String> states,
    String initial,
    List<String> terminalStates,
    List<TransitionRuleDefinition> transitions)
    implements DslObject {

}
