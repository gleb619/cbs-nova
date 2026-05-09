package cbs.dsl.builder;

import cbs.dsl.api.DslObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Aggregate builder that collects multiple helpers in a single DSL file. */
public class HelpersBuilder {

  private final List<HelperBuilder> builders = new ArrayList<>();

  public HelpersBuilder helper(String code, Function<HelperBuilder, HelperBuilder> block) {
    builders.add(block.apply(new HelperBuilder(code)));
    return this;
  }

  public List<DslObject> build() {
    return builders.stream().map(HelperBuilder::build).collect(Collectors.toList());
  }
}
