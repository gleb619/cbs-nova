package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

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

  public boolean contains(@NonNull String name) {
    return registry.find(name).isPresent();
  }

  public @NonNull Optional<ProcessDslObject> find(@NonNull String name) {
    return registry.find(name);
  }

  public @NonNull List<String> names() {
    return registry.all().stream().map(ProcessDslObject::name).sorted().toList();
  }
}
