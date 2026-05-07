package cbs.dsl.builder;

import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.ParameterDefinition.ParameterType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParametersBuilder {

  private final List<ParameterDefinition> parameters = new ArrayList<>();

  public ParametersBuilder string(String name) {
    this.parameters.add(ParameterDefinition.mandatory(name, ParameterType.STRING));
    return this;
  }

  public ParametersBuilder number(String name) {
    this.parameters.add(ParameterDefinition.mandatory(name, ParameterType.INTEGER));
    return this;
  }

  public ParametersBuilder decimal(String name) {
    this.parameters.add(ParameterDefinition.mandatory(name, ParameterType.DECIMAL));
    return this;
  }

  public ParametersBuilder bool(String name) {
    this.parameters.add(ParameterDefinition.mandatory(name, ParameterType.BOOLEAN));
    return this;
  }

  public List<ParameterDefinition> build() {
    return Collections.unmodifiableList(new ArrayList<>(parameters));
  }
}