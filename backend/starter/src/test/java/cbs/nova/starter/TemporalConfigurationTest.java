package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TemporalConfiguration.class)
class TemporalConfigurationTest {

  @Autowired
  private WorkflowClient workflowClient;

  @Test
  void loadsTemporalClient() {
    assertThat(workflowClient).isNotNull();
  }
}
