package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class NoopSaga implements DslSaga {

  public static final NoopSaga INSTANCE = new NoopSaga();

  private NoopSaga() {
  }

  @Override
  public void addCompensation(@NonNull Runnable compensation) {
  }

  @Override
  public void compensate() {
  }

  @Override
  public boolean hasCompensations() {
    return false;
  }

  @Override
  public boolean isNoop() {
    return true;
  }
}
