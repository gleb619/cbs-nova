package cbs.dsl.builder;

public final class Dsl {

  private Dsl() {}

  public static ConditionBuilder condition(String code) {
    return new ConditionBuilder(code);
  }

  public static ContextBuilder context() {
    return new ContextBuilder();
  }

  public static EventBuilder event(String code) {
    return new EventBuilder(code);
  }

  public static HelpersBuilder helpers() {
    return new HelpersBuilder();
  }

  public static MassOperationBuilder massOperation(String code) {
    return new MassOperationBuilder(code);
  }

  public static ParametersBuilder parameters() {
    return new ParametersBuilder();
  }

  public static TransactionBuilder transaction(String code) {
    return new TransactionBuilder(code);
  }

  public static WorkflowBuilder workflow(String code) {
    return new WorkflowBuilder(code);
  }
}
