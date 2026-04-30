package cbs.dsl.api;

import cbs.dsl.api.context.TransactionsScope;
import lombok.Builder;

import java.util.function.Consumer;

/**
 * Defines a workflow transition rule — when an event is triggered in a given state, what state to
 * transition to, and what to do on fault.
 */
@Builder
public record TransitionRuleDefinition(
    String from,
    String to,
    Action on,
    EventDefinition event,
    String onFault,
    Consumer<TransactionsScope> onFaultBlock) {

  public TransitionRuleDefinition {
    if (onFault == null) {
      onFault = "FAULTED";
    }
  }

  public TransitionRuleDefinition(
      String from, String to, Action on, EventDefinition event, String onFault) {
    this(from, to, on, event, onFault, null);
  }

  public TransitionRuleDefinition(String from, String to, Action on, EventDefinition event) {
    this(from, to, on, event, "FAULTED", null);
  }

  public String getFrom() {
    return from;
  }

  public String getTo() {
    return to;
  }

  public Action getOn() {
    return on;
  }

  public EventDefinition getEvent() {
    return event;
  }

  public String getOnFault() {
    return onFault;
  }

  public Consumer<TransactionsScope> getOnFaultBlock() {
    return onFaultBlock;
  }
}
