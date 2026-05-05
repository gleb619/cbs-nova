package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.ParameterDefinition;

import java.util.List;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Concrete DSL object for helpers — implements {@link HelperDefinition} and {@link DslObject}. */
@Getter
@RequiredArgsConstructor
public class HelperDslObject implements DslObject {

  private final String code;
  private final List<ParameterDefinition> parameters;
  private final Function<HelperInput, HelperOutput> previewBlock;
  private final Function<HelperInput, HelperOutput> executeBlock;

}
