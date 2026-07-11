package cbs.nova.dsl.transaction;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslEntityNotFoundException;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.TransactionRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public final class TransactionManager {

  private final TransactionRegistry registry;
  private final TransactionRunner runner;

  public void register(@NonNull TransactionDslObject tx) {
    registry.register(tx);
  }

  public @NonNull Result<?> execute(@NonNull String name, @NonNull Context<?> ctx) {
    return registry
            .find(name)
            .map(t -> runner.run(t, ctx))
            .orElse(Result.failure(
                    new DslEntityNotFoundException(ctx.runId(), "Transaction not found: " + name)));
  }

  public boolean contains(@NonNull String name) {
    return registry.find(name).isPresent();
  }

  public @NonNull Optional<TransactionDslObject> find(@NonNull String name) {
    return registry.find(name);
  }

  public @NonNull List<String> names() {
    return registry.all().stream().map(TransactionDslObject::name).sorted().toList();
  }
}
