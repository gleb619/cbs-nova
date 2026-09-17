package cbs.nova.dsl;
import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.exception.DslEntityNotFoundException;
import cbs.nova.dsl.registry.DefaultCompensationRegistry;
import cbs.nova.dsl.registry.DefaultTransactionRegistry;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.transaction.TransactionDslObject;
import cbs.nova.dsl.transaction.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionManagerTest {

  private TransactionManager manager;

  @BeforeEach
  void setUp() {
    manager = new TransactionManager(new DefaultTransactionRegistry(),
            new DefaultTransactionRunner(new DefaultCompensationRegistry()));
  }

  private TransactionDslObject tx(String name) {
    return Dsl.transaction(name).execute(ctx -> Result.success("result-" + name))
            .build();
  }

  @Test
  void executeRunsRegisteredTransaction() {
    manager.register(tx("PayTx"));
    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.RUN).runId("run-1").build();
    var result = manager.execute("PayTx", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("result-PayTx");
  }

  @Test
  void executeReturnsFailureForUnknownName() {
    var ctx = SimpleContext.builder().body("input").mode(ExecutionMode.RUN).runId("run-2").build();
    var result = manager.execute("NoSuch", ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslEntityNotFoundException.class);
    assertThat(result.cause().getMessage()).contains("NoSuch");
  }

  @Test
  void containsReturnsTrueAfterRegister() {
    manager.register(tx("KycTx"));
    assertThat(manager.contains("KycTx")).isTrue();
  }

  @Test
  void containsReturnsFalseForUnknown() {
    assertThat(manager.contains("ghost")).isFalse();
  }

  @Test
  void namesReturnsSortedNames() {
    manager.register(tx("Beta"));
    manager.register(tx("Alpha"));
    assertThat(manager.names()).containsExactly("Alpha", "Beta");
  }
}
