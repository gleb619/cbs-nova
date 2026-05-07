package cbs.dsl.api;

import cbs.dsl.api.ContextTypes.ContextInput;
import cbs.dsl.api.ContextTypes.ContextOutput;
import java.util.List;
import java.util.function.Function;

public interface StandardDslObject extends DslObject {

  String code();

  String name();

  List<ParameterDefinition> parameters();

  Function<ContextInput, ContextOutput> contextBlock();

}
