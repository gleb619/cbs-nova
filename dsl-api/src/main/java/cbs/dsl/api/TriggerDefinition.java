package cbs.dsl.api;

import lombok.Getter;

import java.time.Instant;

/**
 * Defines a trigger for scheduling mass operations.
 *
 * <p>This is an abstract type with concrete subclasses for different trigger kinds.
 */
public abstract class TriggerDefinition {

  private TriggerDefinition() {}

  /** A cron-expression-based trigger. */
  @Getter
  public static final class CronTrigger extends TriggerDefinition {
    private final String expression;

    public CronTrigger(String expression) {
      this.expression = expression;
    }
  }

  /** A one-time trigger at a specific instant. */
  @Getter
  public static final class OnceTrigger extends TriggerDefinition {
    private final Instant at;

    public OnceTrigger(Instant at) {
      this.at = at;
    }
  }

  /** A periodic trigger defined by days, hours, and minutes. */
  @Getter
  public static final class EveryTrigger extends TriggerDefinition {
    private final int days;
    private final int hours;
    private final int minutes;

    public EveryTrigger(int days, int hours, int minutes) {
      this.days = days;
      this.hours = hours;
      this.minutes = minutes;
    }
  }

  /** A signal-based trigger. */
  @Getter
  public static final class SignalTrigger extends TriggerDefinition {
    private final SignalTypes.Signal signal;

    public SignalTrigger(SignalTypes.Signal signal) {
      this.signal = signal;
    }
  }
}
