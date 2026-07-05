package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DslWorkerConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
          .withUserConfiguration(DslWorkerConfiguration.class);

  @Test
  void workerBeanNotCreatedWhenDisabled() {
    contextRunner
            .withPropertyValues("dsl.worker.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(Worker.class));
  }

  @Test
  void workerBeanNotCreatedWhenPropertyAbsent() {
    contextRunner.run(ctx -> assertThat(ctx).doesNotHaveBean(Worker.class));
  }
}
