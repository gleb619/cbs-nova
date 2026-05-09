package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.HelperContext;
import lombok.Builder;

import java.util.List;
import java.util.function.Function;

@Builder(toBuilder = true)
public record HelperDslObject(
    String code,
    List<ParameterDefinition> parameters,
    Function<HelperContext<HelperInput>, HelperContext<HelperOutput>> previewBlock,
    Function<HelperContext<HelperInput>, HelperContext<HelperOutput>> executeBlock)
    implements DslObject {

}
