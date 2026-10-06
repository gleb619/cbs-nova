package cbs.nova.dsl;

import cbs.nova.dsl.exception.DslSagaCompensationException;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public interface DslSaga {

  void addCompensation(@NonNull Runnable compensation);

  void compensate();

  boolean hasCompensations();

  default boolean isNoop() {
    return false;
  }

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
      List<Throwable> failures = new ArrayList<>();
      List<Runnable> reversed = snapshot.reversed();

      // Deterministic sequential execution. parallelStream uses the common ForkJoinPool
      // and is unsafe inside Temporal workflow threads, so the parallelCompensation flag
      // is retained for API compatibility but currently executes sequentially.
      for (Runnable compensation : reversed) {
        try {
          compensation.run();
        } catch (Throwable t) {
          failures.add(t);
        }
      }

      if (!failures.isEmpty()) {
        Throwable first = failures.getFirst();
        var aggregate = new DslSagaCompensationException(
                failures.size() + " compensation(s) failed; see suppressed exceptions",
                first);
        for (int i = 1; i < failures.size(); i++) {
          aggregate.addSuppressed(failures.get(i));
        }
        throw aggregate;
      }
    }

    @Override
    public boolean hasCompensations() {
      return !compensations.isEmpty();
    }
  }
}
