package cbs.dsl.builder;

public final class ContextDsl {

  private ContextDsl() {}

  public static ContextBuilder context() {
    return new ContextBuilder();
  }
}
