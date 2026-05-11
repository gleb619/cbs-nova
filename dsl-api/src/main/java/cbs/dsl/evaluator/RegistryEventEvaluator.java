package cbs.dsl.evaluator;

import cbs.dsl.api.DefinitionRegistry;
import cbs.dsl.api.context.EventEvaluator;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class RegistryEventEvaluator implements EventEvaluator {

  private final DefinitionRegistry registry;

  @Override
  public <U> U evaluate(String code, Map<String, Object> params) {
    return null;
  }
}
