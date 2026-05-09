package cbs.dsl.evaluator;

import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.EventTypes.EventOutput;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.builder.EventDslObject;

/**
 * Evaluates an {@link EventDslObject} DSL descriptor at runtime.
 *
 * <p>This is the runtime interpreter for REFLECTED mode — it executes the DSL blocks (context,
 * display, transactions, finish) directly without generated Temporal workflows.
 */
public class EventEvaluator {

  /**
   * Evaluates the context enrichment block of the given event DSL object.
   *
   * @param dsl the event DSL object
   * @param ctx the enrichment context
   */
  public static void evaluateContext(EventDslObject dsl, EnrichmentContext ctx) {
    if (dsl != null && dsl.contextBlock() != null) {
      dsl.contextBlock().accept(ctx);
    }
  }

  /**
   * Builds an {@link EventOutput} from the enriched context.
   *
   * @param dsl the event DSL object
   * @param input the event input
   * @param ctx the enrichment context (after context block has run)
   * @return the event output
   */
  public static EventOutput evaluate(EventDslObject dsl, EventInput input, EnrichmentContext ctx) {
    evaluateContext(dsl, ctx);
    //return new EventOutput(ctx.enrichment());
    return null;
  }
}
