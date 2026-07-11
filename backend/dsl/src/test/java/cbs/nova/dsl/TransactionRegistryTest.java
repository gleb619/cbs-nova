package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.registry.DefaultTransactionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionRegistryTest {

  private DefaultTransactionRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DefaultTransactionRegistry();
  }

  private TransactionDslObject tx(String name) {
    return Dsl.transaction(name).execute(ctx -> Result.success("ok")).build();
  }

  @Test
  void storesAndFindsTransaction() {
    registry.register(tx("PayTx"));
    assertThat(registry.find("PayTx")).isPresent();
  }

  @Test
  void findReturnsEmptyForUnknown() {
    assertThat(registry.find("unknown")).isEmpty();
  }

  @Test
  void allReturnsSingleRegistered() {
    registry.register(tx("TxA"));
    assertThat(registry.all()).hasSize(1);
  }

  @Test
  void rejectsDuplicateRegistration() {
    registry.register(tx("TxDup"));
    assertThatThrownBy(() -> registry.register(tx("TxDup")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TxDup");
  }

  @Test
  void allReturnsAllRegistered() {
    registry.register(tx("TxA"));
    registry.register(tx("TxB"));
    assertThat(registry.all()).hasSize(2);
  }
}
