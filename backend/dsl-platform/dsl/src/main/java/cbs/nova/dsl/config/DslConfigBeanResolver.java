package cbs.nova.dsl.config;

import cbs.nova.dsl.BeanResolver;
import cbs.nova.dsl.explain.ExplainResourceResolver;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.jsonschema.JsonSchemaGenerator;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.dsl.utils.ExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.function.Function;

@RequiredArgsConstructor
final class DslConfigBeanResolver implements BeanResolver {

  private static final Map<Class<?>, Function<DslConfig, Object>> ACCESSORS = Map.of(
          ExplainResourceResolver.class, config -> config.explainResourceResolver().get(),
          ExpressionEvaluator.class, config -> config.expressionEvaluator().get(),
          TemporalProcessLauncher.class, config -> config.temporalProcessLauncher().get(),
          TransactionInvoker.class, config -> config.transactionInvoker().get(),
          HelperInstanceResolver.class, config -> config.helperInstanceResolver().get(),
          JsonSchemaGenerator.class, config -> config.jsonSchemaGenerator().get());

  private final DslConfig config;

  @Override
  public @NonNull Object resolve(@NonNull Class<?> type) {
    Function<DslConfig, Object> accessor = ACCESSORS.get(type);
    if (accessor == null) {
      throw new IllegalStateException("No bean of type " + type.getName() + " is available");
    }
    return accessor.apply(config);
  }
}
