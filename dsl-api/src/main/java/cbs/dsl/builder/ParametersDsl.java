package cbs.dsl.builder;

public final class ParametersDsl {

  private ParametersDsl() {}

  public static ParametersBuilder context() {
    return new ParametersBuilder();
  }
}
