package cbs.nova.dsl.transaction;

import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

public interface TransactionRegistry {

  void register(@NonNull TransactionDslObject transaction);

  @NonNull
  Optional<TransactionDslObject> find(@NonNull String name);

  @NonNull
  Collection<TransactionDslObject> all();
}
