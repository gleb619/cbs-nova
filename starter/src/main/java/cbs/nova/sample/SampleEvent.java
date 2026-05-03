package cbs.nova.sample;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.DisplayScope;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Sample event definition for the PoC.
 *
 * <p>Defines a simple event that calls {@code SAMPLE_HELPER} in its context block and
 * {@code SAMPLE_TX} in its transactions block. In the new architecture, events are defined via DSL
 * files and processed by Layer 2 (DslCompiler), not by {@code @DslComponent}.
 */
public class SampleEvent implements EventDefinition {

  @Override
  public String getCode() {
    return "SAMPLE_EVENT";
  }

  @Override
  public List<ParameterDefinition> getParameters() {
    return List.of(
        ParameterDefinition.builder().name("name").required(false).build(),
        ParameterDefinition.builder().name("value").required(false).build());
  }

  @Override
  public Consumer<EnrichmentContext> getContextBlock() {
    return ctx -> {
      var helperResult =
          ctx.helper("SAMPLE_HELPER", Map.of("name", ctx.getOrDefault("name", "World")));
      ctx.put("greeting", ((Map<?, ?>) helperResult).get("result"));
    };
  }

  @Override
  public Consumer<DisplayScope> getDisplayBlock() {
    return scope -> {};
  }

  @Override
  public Consumer<TransactionsScope> getTransactionsBlock() {
    return null;
  }

  @Override
  public List<String> getTransactionCodes() {
    return List.of("SAMPLE_TX");
  }

  @Override
  public BiConsumer<FinishContext, Throwable> getFinishBlock() {
    return (ctx, ex) -> {
      if (ex == null) {
        ctx.println("Event completed: " + ctx.get("greeting"));
      }
    };
  }
}
