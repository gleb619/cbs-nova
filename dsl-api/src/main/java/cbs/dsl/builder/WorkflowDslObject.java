package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.TransitionRuleDefinition;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record WorkflowDslObject(
    String code,
    List<String> states,
    String initial,
    List<String> terminalStates,
    List<TransitionRuleDefinition> transitions)
    implements DslObject {}
