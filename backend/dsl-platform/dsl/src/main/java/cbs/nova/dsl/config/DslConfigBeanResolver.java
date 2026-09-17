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

@RequiredArgsConstructor
final class DslConfigBeanResolver implements BeanResolver {

  private final DslConfig config;

  @Override
  // TODO: add some reflection with cache for `DslConfig` for a better support
  public @NonNull Object resolve(@NonNull Class<?> type) {
    if (type == ExplainResourceResolver.class) {
      return config.explainResourceResolver().get();
    }
    if (type == ExpressionEvaluator.class) {
      return config.expressionEvaluator().get();
    }
    if (type == TemporalProcessLauncher.class) {
      return config.temporalProcessLauncher().get();
    }
    if (type == TransactionInvoker.class) {
      return config.transactionInvoker().get();
    }
    if (type == HelperInstanceResolver.class) {
      return config.helperInstanceResolver().get();
    }
    if (type == JsonSchemaGenerator.class) {
      return config.jsonSchemaGenerator().get();
    }
    throw new IllegalStateException("No bean of type " + type.getName() + " is available");
  }
}
