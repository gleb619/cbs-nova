package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.RetryPolicyFactory;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DslBuilderTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final RetryPolicyFactory retryPolicyFactory = new RetryPolicyFactory();

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
    var policy = retryPolicyFactory.defaults();
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
    GlobalManager.globalManager().resetForTests();
    var gm = GlobalManager.globalManager();
    gm.registerHelper("greeter", ctx -> Result.success("hello"));

    var proc = Dsl.process("GreetProc")
            .input(String.class).output(String.class)
            .execute(ctx -> ctx.runHelper("greeter"))
            .build();
    gm.registerProcess(proc);

    var result = gm.runProcess("GreetProc",
            contextFactory.of("in", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("hello");
  }

  @Test
  void processWithParametersBuildsSuccessfully() {
    var obj = Dsl.process("ParamProcess")
            .parameters(reg -> reg.string("name").number("amount"))
            .execute(ctx -> Result.success("done"))
            .build();
    assertThat(obj.parameters()).hasSize(2);
    assertThat(obj.parameters().get(0).name()).isEqualTo("name");
    assertThat(obj.parameters().get(0).type()).isEqualTo(ParameterType.STRING);
    assertThat(obj.parameters().get(1).name()).isEqualTo("amount");
    assertThat(obj.parameters().get(1).type()).isEqualTo(ParameterType.NUMBER);
    assertThat(obj.inputType()).isNull();
    assertThat(obj.outputType()).isNull();
  }

  @Test
  void transactionWithParametersBuildsSuccessfully() {
    var obj = Dsl.transaction("ParamTx")
            .parameters(reg -> reg.string("customerId").bool("verified"))
            .execute(ctx -> Result.success("ok"))
            .build();
    assertThat(obj.parameters()).hasSize(2);
    assertThat(obj.parameters().get(1).type()).isEqualTo(ParameterType.BOOLEAN);
    assertThat(obj.inputType()).isNull();
  }

  @Test
  void functionWithParametersBuildsSuccessfully() {
    var obj = Dsl.function("ParamFn")
            .parameters(reg -> reg.object("data", String.class))
            .execute(ctx -> Result.success("fn-ok"))
            .build();
    assertThat(obj.parameters()).hasSize(1);
    assertThat(obj.parameters().get(0).type()).isEqualTo(ParameterType.OBJECT);
    assertThat(obj.parameters().get(0).objectType()).isEqualTo(String.class);
  }

  @Test
  void processMixingParametersAndInputThrows() {
    assertThatThrownBy(() -> Dsl.process("Bad")
            .input(String.class)
            .parameters(reg -> reg.string("name"))
            .execute(ctx -> Result.success("ok"))
            .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot have both");
  }

  @Test
  void typedProcessHasNullParameters() {
    var obj = Dsl.process("TypedProc")
            .input(String.class).output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    assertThat(obj.parameters()).isNull();
  }

  @Test
  void parameterBasedProcessReceivesMapBodyFromMapInput() {
    GlobalManager.globalManager().resetForTests();
    var gm = GlobalManager.globalManager();

    var proc = Dsl.process("ParamProcess")
            .parameters(reg -> reg.string("name"))
            .execute(ctx -> {
              Map<String, Object> body = ctx.body();
              return Result.success("hello " + body.get("name"));
            })
            .build();
    gm.registerProcess(proc);

    var result = gm.runProcess("ParamProcess",
            contextFactory.of(MapInput.of("name", "world"), ExecutionMode.PREVIEW));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("hello world");
  }

  @Test
  void parameterBasedProcessRunHelperWithMapInput() {
    GlobalManager.globalManager().resetForTests();
    var gm = GlobalManager.globalManager();
    gm.registerHelper("echo", ctx -> Result.success(ctx.body()));

    var proc = Dsl.process("ParamProcess")
            .parameters(reg -> reg.string("name"))
            .execute(ctx -> ctx.runHelper("echo", MapInput.of("name", "test")))
            .build();
    gm.registerProcess(proc);

    var result = gm.runProcess("ParamProcess",
            contextFactory.of(MapInput.of("name", "ignored"), ExecutionMode.PREVIEW));

    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> value = (Map<String, Object>) result.value();
    assertThat(value).containsEntry("name", "test");
  }

  @Test
  void typedProcessBodyIsAvailableWithoutCast() {
    GlobalManager.globalManager().resetForTests();
    var gm = GlobalManager.globalManager();

    var proc = Dsl.process("TypedBodyProc")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("got " + ctx.body().toUpperCase()))
            .build();
    gm.registerProcess(proc);

    var result = gm.runProcess("TypedBodyProc",
            contextFactory.of("world", ExecutionMode.PREVIEW));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("got WORLD");
  }

  @AfterEach
  void cleanup() {
    GlobalManager.globalManager().resetForTests();
  }
}
