package cbs.dsl.evaluator;

import cbs.dsl.api.DefinitionRegistry;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.context.Context;
import cbs.dsl.api.context.EventContext;
import cbs.dsl.api.context.EventEvaluator;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;
import cbs.dsl.builder.EventDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Evaluates a {@link EventDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets the transactions block directly. Holds a reference to the
 * {@link DefinitionRegistry} so that nested transaction resolution can be performed.
 */
@RequiredArgsConstructor
public class RegistryEventEvaluator implements EventEvaluator {

  private final DefinitionRegistry registry;

  /**
   * Resolves an event definition by code from the registry.
   *
   * @param code the event code
   * @return the event definition
   * @throws IllegalArgumentException if not found
   */
  @NonNull
  public EventDefinition resolveEvent(@NonNull String code) {
    return registry.resolveEvent(code);
  }

  /**
   * Evaluates the context block of the given event DSL object.
   *
   * @param dsl the event DSL object
   * @param ctx the event context
   * @return the enriched event context
   */
  public EventContext evaluateContext(@NonNull EventDslObject dsl, @NonNull EventContext ctx) {
    if (dsl.contextBlock() != null) {
      Context context = Context.builder()
          .eventNumber(ctx.eventNumber())
          .performedBy(ctx.performedBy())
          .params(ctx.params())
          .helperEvaluator(ctx.helperEvaluator())
          .build();
      Context enriched = dsl.contextBlock().apply(context);
      return ctx.toBuilder().params(enriched.params()).build();
    }
    return ctx.copy();
  }

  /**
   * Evaluates the transactions block of the given event DSL object.
   *
   * @param dsl the event DSL object
   * @param input the event context
   * @return the event context result
   */
  public EventContext evaluateTransactions(@NonNull EventDslObject dsl, @NonNull EventInput input) {
    if (dsl.transactionsBlock() != null) {
      //      SimpleTransactionsScope scope = new SimpleTransactionsScope(input);
      //      dsl.transactionsBlock().accept(scope);
      //      return scope.toContext();
    }
    //    return input.copy();

    return null;
  }

  /**
   * Evaluates the finish block of the given event DSL object.
   *
   * @param dsl the event DSL object
   * @param ctx the event context
   * @param ex the throwable, if any
   */
  public void evaluateFinish(@NonNull EventDslObject dsl, @NonNull EventContext ctx, Throwable ex) {
    if (dsl.finishBlock() != null) {
      FinishContext finishCtx = FinishContext.builder()
          .eventNumber(ctx.eventNumber())
          .performedBy(ctx.performedBy())
          .params(ctx.params())
          .helperEvaluator(ctx.helperEvaluator())
          .build();
      dsl.finishBlock().accept(finishCtx, ex);
    }
  }

  @Override
  public <U> U evaluate(String code, Map<String, Object> params) {
    EventDefinition definition = resolveEvent(code);
    EventDslObject dsl = (EventDslObject) definition.dsl();
    EventContext ctx = EventContext.builder().params(params).build();
    EventContext resultCtx = evaluateContext(dsl, ctx);
    //    resultCtx = evaluateTransactions(dsl, resultCtx);
    //    evaluateFinish(dsl, resultCtx, null);
    //    return (U) EventOutput.success(resultCtx.params());

    return null;
  }

  private static class SimpleTransactionsScope implements TransactionsScope {

    private final EventContext baseContext;
    private EventContext currentContext;

    SimpleTransactionsScope(EventContext baseContext) {
      this.baseContext = baseContext;
      this.currentContext = baseContext;
    }

    EventContext toContext() {
      return currentContext.copy();
    }

    @Override
    public CompletableFuture<TransactionsScope.StepHandle> step(
        Function<EventContext, EventContext> fn) {
      EventContext next = fn.apply(currentContext);
      if (next != null) {
        currentContext = next;
      }
      return CompletableFuture.completedFuture(new SimpleStepHandle());
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<TransactionsScope.StepHandle> step(String code) {
      if (baseContext.transactionEvaluator() != null) {
        Map<String, Object> result =
            baseContext.transactionEvaluator().evaluate(code, currentContext.params());
        if (result != null) {
          currentContext.params().putAll(result);
        }
      }
      return CompletableFuture.completedFuture(new SimpleStepHandle());
    }

    @Override
    public CompletableFuture<TransactionsScope.StepHandle> when(Consumer<ConditionalScope> block) {
      return CompletableFuture.completedFuture(new SimpleStepHandle());
    }

    @SafeVarargs
    @Override
    public final void await(CompletableFuture<TransactionsScope.StepHandle>... handles) {
      for (CompletableFuture<TransactionsScope.StepHandle> handle : handles) {
        handle.join();
      }
    }
  }

  private static class SimpleStepHandle implements TransactionsScope.StepHandle {

    @Override
    public CompletableFuture<TransactionsScope.StepHandle> then(
        Function<EventContext, EventContext> fn) {
      return CompletableFuture.completedFuture(this);
    }

    @Override
    public void join() {
      // no-op for simple sequential execution
    }
  }
}
