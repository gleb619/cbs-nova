package cbs.nova.dsl.process;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.exception.DslEntityNotFoundException;
import cbs.nova.dsl.Result;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public final class ProcessManager {

  private final ProcessRegistry registry;
  private final ProcessRunner runner;

  public void register(@NonNull ProcessDslObject process) {
    registry.register(process);
  }

  public @NonNull Result<?> execute(@NonNull String name, @NonNull Context<?> ctx) {
    return registry
            .find(name)
            .map(p -> runner.run(p, ctx))
            .orElse(Result.failure(
                    new DslEntityNotFoundException(ctx.runId(), "Process not found: " + name)));
  }

  public @NonNull Result<?> execute(
          @NonNull String name,
          @NonNull String version,
          @NonNull Context<?> ctx) {
    return registry
            .find(name, version)
            .map(p -> runner.run(p, ctx))
            .orElse(Result.failure(
                    new DslEntityNotFoundException(
                            ctx.runId(),
                            "Process not found: " + name + " version " + version)));
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
