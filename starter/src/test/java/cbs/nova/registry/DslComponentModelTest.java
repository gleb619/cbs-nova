package cbs.nova.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.DslComponentResolver;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.codegen.generated.definitions.SampleTransactionDefinition;
import cbs.nova.sample.SampleTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Tests for the {@code componentModel} attribute of {@link cbs.dsl.api.DslComponent}.
 *
 * <p>Verifies that generated wrappers correctly delegate to a {@link DslComponentResolver} when one
 * is supplied (the SPRING/AUTO-with-Spring-annotations path), and fall back to plain constructor
 * instantiation otherwise (the SIMPLE path).
 */
class DslComponentModelTest {

  @Test
  @DisplayName("Should instantiate via plain constructor when resolver is null")
  void shouldInstantiateViaPlainConstructorWhenResolverIsNull() {
    SampleTransactionDefinition def = new SampleTransactionDefinition();

    TransactionOutput output =
        def.execute(new TransactionInput(null, Map.of("name", "test")));

    assertThat(output.params()).containsEntry("greeting", "Hello, test");
  }

  @Test
  @DisplayName("Should delegate to DslComponentResolver when resolver is provided")
  void shouldDelegateToDslComponentResolverWhenResolverIsProvided() {
    DslComponentResolver resolver = mock(DslComponentResolver.class);
    SampleTransaction resolvedInstance = new SampleTransaction() {
      @Override
      public TransactionContext<SampleTxOutput> execute(TransactionContext<SampleTxInput> input) {
        return new SampleTxOutput("resolved: " + input.name());
      }
    };
    when(resolver.resolve(SampleTransaction.class)).thenReturn(resolvedInstance);

    SampleTransactionDefinition def = new SampleTransactionDefinition(resolver);

    verify(resolver).resolve(SampleTransaction.class);

    TransactionOutput output =
        def.execute(new TransactionInput(null, Map.of("name", "test")));
    assertThat(output.params()).containsEntry("greeting", "resolved: test");
  }

  @Test
  @DisplayName("Should use resolver during SPI registry loading")
  void shouldUseResolverDuringSpiRegistryLoading() {
    DslComponentResolver resolver = mock(DslComponentResolver.class);
    SampleTransaction resolvedInstance = new SampleTransaction() {
      @Override
      public TransactionContext<SampleTxOutput> execute(TransactionContext<SampleTxInput> input) {
        return new SampleTxOutput("spi-resolved: " + input.name());
      }
    };
    when(resolver.resolve(SampleTransaction.class)).thenReturn(resolvedInstance);

    DslRegistry registry = new DslRegistry();
    registry.setComponentResolver(resolver);
    SpiImplRegistryLoader.loadInto(registry, resolver);

    TransactionDefinition txDef = registry.resolveTransaction("SAMPLE_TX");
    TransactionOutput output =
        txDef.execute(new TransactionInput(null, Map.of("name", "test")));

    assertThat(output.params()).containsEntry("greeting", "spi-resolved: test");
  }
}
