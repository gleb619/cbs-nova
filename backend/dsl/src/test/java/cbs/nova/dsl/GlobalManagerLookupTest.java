package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalManagerLookupTest {

  @BeforeEach
  void reset() {
    GlobalManager.getInstance().resetForTests();
  }

  @Test
  void hasProcessReturnsTrueWhenRegistered() {
    GlobalManager.getInstance()
            .registerProcess(Dsl.process("Foo")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    assertThat(GlobalManager.getInstance().hasProcess("Foo")).isTrue();
    assertThat(GlobalManager.getInstance().hasProcess("Bar")).isFalse();
  }

  @Test
  void hasTransactionReturnsTrueWhenRegistered() {
    GlobalManager.getInstance()
            .registerTransaction(
                    Dsl.transaction("FooTx")
                            .input(String.class)
                            .output(String.class)
                            .execute(ctx -> Result.success("ok"))
                            .build());
    assertThat(GlobalManager.getInstance().hasTransaction("FooTx")).isTrue();
    assertThat(GlobalManager.getInstance().hasTransaction("BarTx")).isFalse();
  }

  @Test
  void hasHelperReturnsTrueWhenRegistered() {
    GlobalManager.getInstance().registerHelper("myHelper", ctx -> Result.success("done"));
    assertThat(GlobalManager.getInstance().hasHelper("myHelper")).isTrue();
    assertThat(GlobalManager.getInstance().hasHelper("other")).isFalse();
  }
}
