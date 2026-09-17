package cbs.nova.dsl.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.config.SingletonSupport.Replaceable;
import cbs.nova.dsl.explain.ExplainResourceResolver;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.jsonschema.JsonSchemaGenerator;
import cbs.nova.dsl.process.TemporalProcessLauncher;
import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.dsl.utils.ExpressionEvaluator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

class DslConfigBeanResolverTest {

  private final DslConfig config = new DslConfig(SingletonSupport.SingletonScope.of());
  private final DslConfigBeanResolver resolver = new DslConfigBeanResolver(config);

  @Test
  void resolvesExplainResourceResolver() {
    ExplainResourceResolver stub = stub(ExplainResourceResolver.class);
    config.explainResourceResolver().replace(stub);

    assertThat(resolver.resolve(ExplainResourceResolver.class)).isSameAs(stub);
  }

  @Test
  void resolvesExpressionEvaluator() {
    ExpressionEvaluator stub = stub(ExpressionEvaluator.class);
    config.expressionEvaluator().replace(stub);

    assertThat(resolver.resolve(ExpressionEvaluator.class)).isSameAs(stub);
  }

  @Test
  void resolvesTemporalProcessLauncher() {
    TemporalProcessLauncher stub = stub(TemporalProcessLauncher.class);
    config.temporalProcessLauncher().replace(stub);

    assertThat(resolver.resolve(TemporalProcessLauncher.class)).isSameAs(stub);
  }

  @Test
  void resolvesTransactionInvoker() {
    TransactionInvoker stub = stub(TransactionInvoker.class);
    config.transactionInvoker().replace(stub);

    assertThat(resolver.resolve(TransactionInvoker.class)).isSameAs(stub);
  }

  @Test
  void resolvesHelperInstanceResolver() {
    HelperInstanceResolver stub = stub(HelperInstanceResolver.class);
    config.helperInstanceResolver().replace(stub);

    assertThat(resolver.resolve(HelperInstanceResolver.class)).isSameAs(stub);
  }

  @Test
  void resolvesJsonSchemaGenerator() {
    JsonSchemaGenerator stub = stub(JsonSchemaGenerator.class);
    config.jsonSchemaGenerator().replace(stub);

    assertThat(resolver.resolve(JsonSchemaGenerator.class)).isSameAs(stub);
  }

  @Test
  void unknownTypeThrowsWithSameMessage() {
    assertThatThrownBy(() -> resolver.resolve(String.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No bean of type java.lang.String is available");
  }

  @Test
  void supplierIsInvokedLazilyAtResolveTime() {
    AtomicInteger invocations = new AtomicInteger();
    TransactionInvoker stub = stub(TransactionInvoker.class);
    DslConfig lazyConfig = new DslConfig(SingletonSupport.SingletonScope.of()) {
      @Override
      public Replaceable<TransactionInvoker> transactionInvoker() {
        return Replaceable.of(() -> {
          invocations.incrementAndGet();
          return stub;
        });
      }
    };
    DslConfigBeanResolver lazyResolver = new DslConfigBeanResolver(lazyConfig);

    assertThat(invocations.get()).isZero();

    assertThat(lazyResolver.resolve(TransactionInvoker.class)).isSameAs(stub);
    assertThat(invocations.get()).isEqualTo(1);
  }

  @SuppressWarnings("unchecked")
  private static <T> T stub(Class<T> type) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> null);
  }
}
