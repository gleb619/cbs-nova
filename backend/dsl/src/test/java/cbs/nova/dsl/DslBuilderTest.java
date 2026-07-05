package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DslBuilderTest {
  @Test
  void processBuildsSuccessfully() {
    var obj = Dsl.process("MyProcess")
            .input(String.class).output(String.class)
            .execute(ctx -> Result.success("done"))
            .build();
    assertThat(obj.name()).isEqualTo("MyProcess");
    assertThat(obj.type()).isEqualTo(DslObject.DslType.PROCESS);
  }

  @Test
  void transactionBuildsSuccessfully() {
    var obj = Dsl.transaction("MyTx")
            .input(String.class).output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    assertThat(obj.type()).isEqualTo(DslObject.DslType.TRANSACTION);
  }

  @Test
  void functionBuildsSuccessfully() {
    var obj = Dsl.function("MyFn")
            .execute(ctx -> Result.success("fn-ok"))
            .build();
    assertThat(obj.type()).isEqualTo(DslObject.DslType.FUNCTION);
  }

  @Test
  void processMissingExecuteThrows() {
    assertThatThrownBy(() -> Dsl.process("Bad").build())
            .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void transactionRetryPolicyFlowsThroughBuilder() {
    var policy = RetryPolicy.defaults();
    var obj = Dsl.transaction("MyTx")
            .input(String.class).output(String.class)
            .execute(ctx -> Result.success("ok"))
            .retryPolicy(policy)
            .build();
    assertThat(obj.retryPolicy()).isEqualTo(policy);
  }

  @Test
  void transactionDefaultRetryPolicyIsNull() {
    var obj = Dsl.transaction("MyTx")
            .input(String.class).output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    assertThat(obj.retryPolicy()).isNull();
  }

  @Test
  void processInvokesRegisteredHelperViaRichContext() {
    GlobalManager.resetForTests();
    var gm = GlobalManager.getInstance();
    gm.registerHelper("greeter", ctx -> Result.success("hello"));

    var proc = Dsl.process("GreetProc")
            .input(String.class).output(String.class)
            .execute(ctx -> ctx.runHelper("greeter"))
            .build();
    gm.registerProcess(proc);

    var result = gm.runProcess("GreetProc", SimpleContext.of("in", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("hello");
  }

  @AfterEach
  void cleanup() {
    GlobalManager.resetForTests();
  }
}
