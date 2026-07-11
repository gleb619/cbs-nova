package cbs.nova.dsl;

import cbs.nova.dsl.transaction.TransactionDslObject;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Optional;

public interface TransactionRegistry {
  void register(@NonNull TransactionDslObject transaction);

  @NonNull
  Optional<TransactionDslObject> find(@NonNull String name);

  @NonNull
  Collection<TransactionDslObject> all();
}
