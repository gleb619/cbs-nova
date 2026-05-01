package cbs.dsl.api;

import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.EventTypes.EventOutput;
import cbs.dsl.api.context.DisplayScope;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Defines an event — a trigger that executes a sequence of transactions within a workflow.
 *
 * <p>This interface is pure metadata. It describes <em>what</em> transactions to run and in which
 * order, but it does <strong>not</strong> execute anything itself. Execution is performed by
 * generated Temporal workflow classes or by the runtime engine in the {@code starter} module.
 */
public interface EventDefinition {

  /**
   * Canonical code used to identify this event in workflow transitions.
   *
   * @return the event code
   */
  String getCode();

  /**
   * List of parameter definitions declared for this event. Used for validation and documentation.
   *
   * @return the parameter definitions
   */
  default List<ParameterDefinition> getParameters() {
    return Collections.emptyList();
  }

  /**
   * Optional context enrichment block that runs before the transaction sequence. Allows events to
   * enrich the context with additional data.
   *
   * @return the context block
   */
  default Consumer<EnrichmentContext> getContextBlock() {
    return ctx -> {};
  }

  /**
   * Optional display block that defines how the event should be presented in the UI.
   *
   * @return the display block
   */
  default Consumer<DisplayScope> getDisplayBlock() {
    return scope -> {};
  }

  /**
   * The transaction sequence block. A {@link Consumer} of {@link TransactionsScope} that describes
   * which transactions to execute and in what order.
   *
   * <p>Returns {@code null} if this event has no transactions.
   *
   * @return the transactions block
   */
  default Consumer<TransactionsScope> getTransactionsBlock() {
    return null;
  }

  /**
   * Ordered list of transaction codes for this event. Used by the orchestrator to dispatch
   * transactions to the {@link cbs.app.temporal.activity.TransactionActivity}.
   *
   * <p>Returns {@code null} if this event has no transactions.
   *
   * @return the transaction codes
   */
  default List<String> getTransactionCodes() {
    return null;
  }

  /**
   * Optional finish block that runs after all transactions complete (success or failure).
   *
   * @return the finish block
   */
  default BiConsumer<FinishContext, Throwable> getFinishBlock() {
    return (ctx, ex) -> {};
  }

  /**
   * Executes this event with the given input.
   *
   * <p>The default implementation returns an empty output. Generated Definition wrappers delegate
   * to the underlying {@link EventFunction}.
   *
   * @param input the event input
   * @return the event output
   */
  default EventOutput execute(EventInput input) {
    return new EventOutput(Collections.emptyMap());
  }
}
