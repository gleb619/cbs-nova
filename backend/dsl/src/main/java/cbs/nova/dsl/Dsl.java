package cbs.nova.dsl;

public final class Dsl {
  private Dsl() {
  }

  public static ProcessBuilder process(String name) {
    return new ProcessBuilder(name);
  }
  public static TransactionBuilder transaction(String name) {
    return new TransactionBuilder(name);
  }
  public static FunctionBuilder function(String name) {
    return new FunctionBuilder(name);
  }
}
