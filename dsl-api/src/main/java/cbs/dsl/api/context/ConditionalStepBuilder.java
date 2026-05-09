package cbs.dsl.api.context;

import cbs.dsl.api.TransactionDefinition;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// TODO: remove
@Deprecated(forRemoval = true)
public interface ConditionalStepBuilder {

  WhenClause when(BooleanSupplier predicate);

  interface WhenClause {
    ConditionalStepBuilder then(Consumer<ConditionalStepBuilder> block);
  }

  WhenClause orWhen(BooleanSupplier predicate);

  void otherwise(Consumer<ConditionalStepBuilder> block);

  void transaction(TransactionDefinition tx);
}
