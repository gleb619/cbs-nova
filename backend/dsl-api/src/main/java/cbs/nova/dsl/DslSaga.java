package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DSL counterpart of {@code io.temporal.workflow.Saga}: a LIFO container of compensating actions
 * that can be executed in reverse order on failure. Compensations are captured as {@link Runnable}
 * closures so the DSL can register them right after a forward step succeeds.
 */
public interface DslSaga {

  /**
   * Registers a compensation action. Actions are executed last-in-first-out by
   * {@link #compensate()} unless {@link Options#parallelCompensation()} is enabled.
   */
  void addCompensation(@NonNull Runnable compensation);

  /**
   * Executes registered compensations. By default they run sequentially in reverse order.
   * Once invoked the compensation list is cleared.
   */
  void compensate();

  /**
   * Returns {@code true} if at least one compensation has been registered.
   */
  boolean hasCompensations();

  static @NonNull DslSaga create() {
    return new DefaultDslSaga(Options.DEFAULT);
  }

  static @NonNull DslSaga create(@NonNull Options options) {
    return new DefaultDslSaga(options);
  }

  record Options(boolean parallelCompensation) {

    public static final Options DEFAULT = new Options(false);

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {

      private boolean parallelCompensation;

      public Builder setParallelCompensation(boolean parallelCompensation) {
        this.parallelCompensation = parallelCompensation;
        return this;
      }

      public Options build() {
        return new Options(parallelCompensation);
      }
    }
  }

  final class DefaultDslSaga implements DslSaga {

    private final Options options;
    private final List<Runnable> compensations = new CopyOnWriteArrayList<>();

    DefaultDslSaga(Options options) {
      this.options = options;
    }

    @Override
    public void addCompensation(@NonNull Runnable compensation) {
      compensations.add(compensation);
    }

    @Override
    public void compensate() {
      List<Runnable> snapshot = new ArrayList<>(compensations);
      compensations.clear();
      List<Runnable> reversed = snapshot.reversed();
      if (options.parallelCompensation()) {
        reversed.parallelStream().forEach(Runnable::run);
      } else {
        for (Runnable compensation : reversed) {
          compensation.run();
        }
      }
    }

    @Override
    public boolean hasCompensations() {
      return !compensations.isEmpty();
    }
  }
}
