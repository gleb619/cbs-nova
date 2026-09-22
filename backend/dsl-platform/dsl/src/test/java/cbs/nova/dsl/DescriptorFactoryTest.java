package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.config.RetryPolicyFactory;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.model.MapInput;
import cbs.nova.dsl.model.ObjectDescriptor;
import cbs.nova.dsl.model.MapOutput;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class DescriptorFactoryTest {

  private final RetryPolicyFactory retryPolicyFactory = new RetryPolicyFactory();

  @Test
  void processDescriptorHasCorrectFields() {
    var obj = Dsl.process("P1")
            .input(String.class)
            .output(Integer.class)
            .version("v2")
            .taskQueue("my-queue")
            .execute(ctx -> Result.success(1))
            .compensation((ctx, history) -> {
            })
            .build();
    var desc = new DescriptorFactory().fromProcess(obj);
    assertThat(desc.name()).isEqualTo("P1");
    assertThat(desc.version()).isEqualTo("v2");
    assertThat(desc.taskQueue()).isEqualTo("my-queue");
    assertThat(desc.inputType()).isEqualTo(String.class);
    assertThat(desc.outputType()).isEqualTo(Integer.class);
    assertThat(desc.hasCompensation()).isTrue();
    assertThat(desc.helperRefs()).isEmpty();
  }

  @Test
  void processDescriptorFallsBackToMapTypesForParameterBasedProcess() {
    var obj = Dsl.process("MappedP")
            .parameters(reg -> reg.string("k"))
            .execute(ctx -> Result.success(MapOutput.of("k", "v")))
            .build();
    var desc = new DescriptorFactory().fromProcess(obj);
    assertThat(desc.inputType()).isEqualTo(MapInput.class);
    assertThat(desc.outputType()).isEqualTo(MapOutput.class);
  }

  @Test
  void processDescriptorKeepsVoidForUntypedProcess() {
    var obj = Dsl.process("BareP")
            .execute(ctx -> Result.success(null))
            .build();
    var desc = new DescriptorFactory().fromProcess(obj);
    assertThat(desc.inputType()).isNull();
    assertThat(desc.outputType()).isNull();
  }

  @Test
  void transactionDescriptorHasTimeout() {
    var obj = Dsl.transaction("T1")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofMinutes(5))
            .build();
    var desc = new DescriptorFactory().fromTransaction(obj);
    assertThat(desc.name()).isEqualTo("T1");
    assertThat(desc.startToCloseTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(desc.hasCompensation()).isFalse();
  }

  @Test
  void transactionDescriptorHasRetryPolicy() {
    var policy = retryPolicyFactory.defaults();
    var obj = Dsl.transaction("T1")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .retryPolicy(policy)
            .build();
    var desc = new DescriptorFactory().fromTransaction(obj);
    assertThat(desc.name()).isEqualTo("T1");
    assertThat(desc.retryPolicy()).isEqualTo(policy);
  }

  @Test
  void functionDescriptorHasName() {
    var obj = Dsl.function("Fn1")
            .execute(ctx -> Result.success("x"))
            .build();
    var desc = new DescriptorFactory().fromFunction(obj);
    assertThat(desc.name()).isEqualTo("Fn1");
    assertThat(desc.inputType()).isNull();
    assertThat(desc.outputType()).isNull();
  }

  @Test
  void functionDescriptorHasInputAndOutput() {
    var obj = Dsl.function("FnTyped")
            .input(String.class)
            .output(Integer.class)
            .execute(ctx -> Result.success(1))
            .build();
    var desc = new DescriptorFactory().fromFunction(obj);
    assertThat(desc.name()).isEqualTo("FnTyped");
    assertThat(desc.inputType()).isEqualTo(String.class);
    assertThat(desc.outputType()).isEqualTo(Integer.class);
  }

  @Test
  void functionDescriptorMapsParameterTypes() {
    var obj = Dsl.function("FnMapped")
            .parameters(reg -> reg.string("k"))
            .execute(ctx -> Result.success(MapOutput.of("k", "v")))
            .build();
    var desc = new DescriptorFactory().fromFunction(obj);
    assertThat(desc.inputType()).isEqualTo(MapInput.class);
    assertThat(desc.outputType()).isEqualTo(MapOutput.class);
  }

  @Test
  void functionDescriptorToDslDescriptorIncludesObjectDescriptor() {
    var desc = FunctionDescriptor.builder()
            .name("F")
            .inputType(String.class)
            .outputType(Integer.class)
            .build();
    var dsl = desc.toDslDescriptor();
    assertThat(dsl.name()).isEqualTo("F");
    assertThat(dsl.type()).isEqualTo(DslObject.DslType.FUNCTION);
    assertThat(dsl.inputType()).isEqualTo(String.class);
    assertThat(dsl.outputType()).isEqualTo(Integer.class);
    assertThat(dsl.objectDescriptor()).isSameAs(desc);
  }

  @Test
  void objectDescriptorDefaultToDslDescriptorIncludesSelf() {
    var desc = new ObjectDescriptor() {
      @Override
      public String name() {
        return "O";
      }

      @Override
      public DslObject.DslType type() {
        return DslObject.DslType.OTHER;
      }

      @Override
      public String description() {
        return null;
      }

      @Override
      public Class<?> inputType() {
        return String.class;
      }

      @Override
      public Class<?> outputType() {
        return Integer.class;
      }
    };
    var dsl = desc.toDslDescriptor();
    assertThat(dsl.name()).isEqualTo("O");
    assertThat(dsl.type()).isEqualTo(DslObject.DslType.OTHER);
    assertThat(dsl.inputType()).isEqualTo(String.class);
    assertThat(dsl.outputType()).isEqualTo(Integer.class);
    assertThat(dsl.objectDescriptor()).isSameAs(desc);
  }
}
