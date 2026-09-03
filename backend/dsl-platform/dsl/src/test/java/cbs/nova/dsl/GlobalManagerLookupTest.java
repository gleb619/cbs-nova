package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalManagerLookupTest {

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void hasProcessReturnsTrueWhenRegistered() {
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Foo")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    assertThat(GlobalManager.globalManager().hasProcess("Foo")).isTrue();
    assertThat(GlobalManager.globalManager().hasProcess("Bar")).isFalse();
  }

  @Test
  void hasTransactionReturnsTrueWhenRegistered() {
    GlobalManager.globalManager()
            .registerTransaction(
                    Dsl.transaction("FooTx")
                            .input(String.class)
                            .output(String.class)
                            .execute(ctx -> Result.success("ok"))
                            .build());
    assertThat(GlobalManager.globalManager().hasTransaction("FooTx")).isTrue();
    assertThat(GlobalManager.globalManager().hasTransaction("BarTx")).isFalse();
  }

  @Test
  void hasHelperReturnsTrueWhenRegistered() {
    GlobalManager.globalManager().registerHelper("myHelper", ctx -> Result.success("done"));
    assertThat(GlobalManager.globalManager().hasHelper("myHelper")).isTrue();
    assertThat(GlobalManager.globalManager().hasHelper("other")).isFalse();
  }

  @Test
  void findFilenameReturnsProviderFilename() {
    GeneratedClassProvider provider = new GeneratedClassProvider() {
      @Override
      public GeneratedClassDescriptor descriptor() {
        return new GeneratedClassDescriptor(
                "GenProc",
                DslObject.DslType.PROCESS,
                "v1",
                "default",
                Runnable.class,
                Runnable.class,
                String.class,
                String.class,
                "{}");
      }

      @Override
      public String filename() {
        return "GenProc.java";
      }
    };

    GlobalManager.globalManager().registerGeneratedClass(provider);

    assertThat(GlobalManager.globalManager().findFilename("GenProc"))
            .contains("GenProc.java");
    assertThat(GlobalManager.globalManager().findFilename("nope")).isEmpty();
  }
}
