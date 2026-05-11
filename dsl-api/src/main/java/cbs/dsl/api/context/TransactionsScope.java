package cbs.dsl.api.context;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public interface TransactionsScope {

  CompletableFuture<StepHandle> step(Function<EventContext, EventContext> fn);

  CompletableFuture<StepHandle> when(Consumer<ConditionalScope> block);

  void await(CompletableFuture<StepHandle>... handles);

  interface StepHandle {

    CompletableFuture<StepHandle> then(Function<EventContext, EventContext> fn);

    void join();

  }

  interface ConditionalScope {

    WhenClause is(Function<ConditionContext, ConditionContext> predicate);

    WhenClause or(Function<ConditionContext, ConditionContext> predicate);

    void otherwise(Function<ConditionContext, ConditionContext> predicate);

  }

  interface WhenClause {

    ConditionalScope then(Consumer<ConditionalScope> block);

  }

}
