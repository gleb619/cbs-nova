package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import lombok.Builder;

@Builder(toBuilder = true)
public record ParametersDslObject(String code) implements DslObject {

}
