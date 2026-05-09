package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
import cbs.dsl.api.context.MassOperationContext;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.DisplayScope;
import cbs.dsl.api.context.TransactionsScope;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.Action;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.MassOperationTypes.MassOperationInput;
import cbs.dsl.api.MassOperationTypes.MassOperationOutput;
import cbs.dsl.api.SignalTypes;
import cbs.dsl.api.SourceDefinition;
import cbs.dsl.api.LockDefinition;
import cbs.dsl.api.TriggerDefinition;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

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

  public static HelperBuilder helper(String code) {
    return new HelperBuilder(code);
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