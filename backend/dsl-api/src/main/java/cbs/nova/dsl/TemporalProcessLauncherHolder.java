package cbs.nova.dsl;

import org.jspecify.annotations.Nullable;

public final class TemporalProcessLauncherHolder {

  private static volatile @Nullable TemporalProcessLauncher launcher;

  private TemporalProcessLauncherHolder() {
  }

  public static void set(@Nullable TemporalProcessLauncher launcher) {
    TemporalProcessLauncherHolder.launcher = launcher;
  }

  public static @Nullable TemporalProcessLauncher get() {
    return launcher;
  }

  public static void reset() {
    launcher = null;
  }
}
