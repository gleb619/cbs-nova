package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

public final class ProcessManager {
  private final ProcessRegistry registry;
  private final ProcessRunner runner;

  public ProcessManager(@NonNull ProcessRegistry registry, @NonNull ProcessRunner runner) {
    this.registry = registry;
    this.runner = runner;
  }

  public void register(@NonNull ProcessDslObject process) {
    registry.register(process);
  }

  public @NonNull Result<?> execute(@NonNull String name, @NonNull Context<?> ctx) {
    return registry
            .find(name)
            .map(p -> runner.run(p, ctx))
            .orElse(Result.failure(new IllegalArgumentException("Process not found: " + name)));
  }
}
