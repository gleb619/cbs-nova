package cbs.nova.dsl.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.transaction.TransactionDslObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class DefaultTransactionRegistryTest {

  private DefaultTransactionRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DefaultTransactionRegistry();
  }

  private static TransactionDslObject transaction(String name) {
    return new TransactionDslObject(
            name,
            "default",
            "v1",
            String.class,
            String.class,
            null,
            ctx -> null,
            null,
            Duration.ofMinutes(1),
            null,
            null,
            null,
            null);
  }

  @Test
  void registerIsFoundByFind() {
    var expected = transaction("PayTx");

    registry.register(expected);

    assertThat(registry.find("PayTx")).contains(expected);
  }

  @Test
  void findReturnsEmptyForUnknownName() {
    assertThat(registry.find("nope")).isEmpty();
  }

  @Test
  void registerSameNameThrows() {
    registry.register(transaction("PayTx"));

    assertThatThrownBy(() -> registry.register(transaction("PayTx")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Transaction already registered: PayTx");
  }

  @Test
  void allReturnsRegisteredTransactions() {
    var first = transaction("AlphaTx");
    var second = transaction("BetaTx");

    registry.register(first);
    registry.register(second);

    assertThat(registry.all()).containsExactlyInAnyOrder(first, second);
  }

  @Test
  void allCollectionIsImmutable() {
    registry.register(transaction("AlphaTx"));
    var snapshot = registry.all();

    assertThatThrownBy(() -> snapshot.add(transaction("BetaTx")))
            .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void noArgConstructorDoesNotThrow() {
    assertThatCode(DefaultTransactionRegistry::new).doesNotThrowAnyException();
  }
}
