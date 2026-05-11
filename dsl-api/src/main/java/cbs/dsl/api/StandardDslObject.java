package cbs.dsl.api;

import cbs.dsl.api.context.Context;

import java.util.List;
import java.util.function.Function;

public interface StandardDslObject extends DslObject {

  String code();

  String name();

  List<ParameterDefinition> parameters();

  Function<Context, Context> contextBlock();
}
