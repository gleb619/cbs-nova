package cbs.nova.dsl.registry;

import cbs.nova.dsl.TransactionRegistry;
import cbs.nova.dsl.transaction.TransactionDslObject;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;

public final class DefaultTransactionRegistry implements TransactionRegistry {
  private final ConcurrentHashMap<String, TransactionDslObject> store = new ConcurrentHashMap<>();

  @Override
  public void register(@NonNull TransactionDslObject transaction) {
    if (store.putIfAbsent(transaction.name(), transaction) != null) {
      throw new IllegalArgumentException("Transaction already registered: " + transaction.name());
    }
  }

  @Override
  public @NonNull Optional<TransactionDslObject> find(@NonNull String name) {
    return Optional.ofNullable(store.get(name));
  }

  @Override
  public @NonNull Collection<TransactionDslObject> all() {
    return store.values();
  }
}
