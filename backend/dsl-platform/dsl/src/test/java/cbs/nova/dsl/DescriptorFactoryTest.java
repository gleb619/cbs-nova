package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.config.RetryPolicyFactory;
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
            .compensation(ctx -> Result.success(null))
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
}
